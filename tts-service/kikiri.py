"""Kikiri Türkçe TTS motoru (duxx/kikiri-turkish-base, Kokoro-82M tabanlı).

Metin eSpeak-ng ile Türkçe fonemlere çevrilir (model de böyle eğitildi), cümlelere bölünür ve
KModel doğrudan fonem dizisiyle çalıştırılır. Çıktı 24 kHz mono MP3 (varsayılan) veya WAV'dır.
"""
from __future__ import annotations

import io
import os
import re
import threading
import wave

import numpy as np
import soundfile as sf
import torch
from huggingface_hub import hf_hub_download
from kokoro import KModel
from misaki import espeak

SAMPLE_RATE = 24_000
REPO = os.environ.get("KIKIRI_REPO", "duxx/kikiri-turkish-base")
MODEL_FILE = os.environ.get("KIKIRI_MODEL_FILE", "models/nisan_epoch7.pth")
VOICES = {"nisan": os.environ.get("KIKIRI_VOICE_FILE", "voices/nisan_stage2_epoch7.pt")}
DEFAULT_VOICE = "nisan"
# Kokoro bağlamı 510 fonemdir; cümleler bundan kısa parçalara bölünür.
MAX_PHONEMES = 400
PAUSE_SECONDS = 0.35


class KikiriEngine:

    def __init__(self, device: str | None = None):
        self.device = device or ("cuda" if torch.cuda.is_available() and os.environ.get("KIKIRI_DEVICE") != "cpu" else "cpu")
        model_path = hf_hub_download(REPO, MODEL_FILE)
        config_path = hf_hub_download(REPO, "config.json")
        model = KModel(repo_id="hexgrad/Kokoro-82M", config=config_path, model=model_path)
        load_weights(model, model_path)
        self.model = model.to(self.device).eval()
        self.vocab = self.model.vocab
        self.voices = {name: torch.load(hf_hub_download(REPO, f), map_location="cpu", weights_only=True)
                       for name, f in VOICES.items()}
        self.g2p = espeak.EspeakG2P(language="tr")
        self._lock = threading.Lock()

    def phonemize(self, text: str) -> str:
        phonemes, _ = self.g2p(text)
        # Model eğitiminde kalın l (ɫ) düz l'ye çevrildi.
        phonemes = phonemes.replace("ɫ", "l")
        return "".join(ch for ch in phonemes if ch in self.vocab)

    def chunks(self, text: str) -> list[str]:
        text = re.sub(r"\s+", " ", text).strip()
        sentences = [s.strip() for s in re.split(r"(?<=[.!?…])\s+", text) if s.strip()]
        out: list[str] = []
        for sentence in sentences:
            ph = self.phonemize(sentence)
            while len(ph) > MAX_PHONEMES:
                cut = ph.rfind(" ", 0, MAX_PHONEMES)
                cut = cut if cut > 0 else MAX_PHONEMES
                out.append(ph[:cut].strip())
                ph = ph[cut:].strip()
            if ph:
                out.append(ph)
        return out

    def synthesize(self, text: str, voice: str | None = None, speed: float = 1.0, fmt: str = "mp3") -> tuple[bytes, float]:
        pack = self.voices.get(voice or DEFAULT_VOICE)
        if pack is None:
            raise ValueError(f"Bilinmeyen ses: {voice}")
        pause = np.zeros(int(SAMPLE_RATE * PAUSE_SECONDS), dtype=np.float32)
        parts: list[np.ndarray] = []
        with self._lock, torch.no_grad():
            for ph in self.chunks(text):
                ref = pack[len(ph) - 1].to(self.device)
                audio = self.model(ph, ref, speed)
                parts.append(audio.detach().cpu().numpy().astype(np.float32))
                parts.append(pause)
        if not parts:
            raise ValueError("Seslendirilecek metin boş")
        samples = np.concatenate(parts[:-1])
        return encode(samples, fmt), len(samples) / SAMPLE_RATE


def _kokoro_key(key: str) -> str:
    key = key[7:] if key.startswith("module.") else key
    return (key.replace(".parametrizations.weight.original0", ".weight_g")
               .replace(".parametrizations.weight.original1", ".weight_v"))


def load_weights(model: KModel, path: str) -> None:
    """Kikiri checkpoint'i yeni PyTorch ağırlık normalizasyonu adlarıyla kaydedilmiş (parametrizations.weight.original0/1).
    Kokoro eski adları (weight_g/weight_v) bekler ve uyuşmayanları sessizce atlar; o zaman model yalnızca gürültü üretir.
    Anahtarlar dönüştürülüp yüklenir; eksik kalan her şey hatadır. Tek istisna eğitimde afin olmayan InstanceNorm'ların
    weight/bias değerleridir: varsayılan 1 ve 0 değerleri eğitimdeki davranışla aynıdır."""
    checkpoint = torch.load(path, map_location="cpu", weights_only=True)
    for part, state in checkpoint.items():
        result = getattr(model, part).load_state_dict({_kokoro_key(k): v for k, v in state.items()}, strict=False)
        missing = [k for k in result.missing_keys if not re.search(r"\.norm\.(weight|bias)$", k)]
        if missing or result.unexpected_keys:
            raise RuntimeError(f"Kikiri ağırlıkları yüklenemedi ({part}): eksik={missing[:5]} fazla={result.unexpected_keys[:5]}")


def encode(samples: np.ndarray, fmt: str) -> bytes:
    buf = io.BytesIO()
    if fmt == "mp3":
        sf.write(buf, np.clip(samples, -1.0, 1.0), SAMPLE_RATE, format="MP3")
        return buf.getvalue()
    pcm = (np.clip(samples, -1.0, 1.0) * 32767).astype("<i2")
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(pcm.tobytes())
    return buf.getvalue()
