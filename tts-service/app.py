"""Masal Dünyası ses servisi: Kikiri Türkçe TTS modelini HTTP üzerinden sunar.

POST /v1/tts   {"text": "...", "voice": "nisan", "speed": 1.0, "format": "mp3"}  ->  audio/mpeg (veya audio/wav)
               Yanıt başlığı X-Audio-Duration: saniye cinsinden süre
GET  /health   model yüklendiyse 200

TTS_SERVICE_TOKEN tanımlıysa istekler "X-TTS-Token: <token>" başlığı ister (Hugging Face Space gibi internete
açık bir adreste zorunlu tutun). Authorization başlığı özel Space'in Hugging Face token'ına bırakılır. Metin kaydedilmez, log'a yazılmaz.
"""
from __future__ import annotations

import os
import secrets
from typing import Literal

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import Response
from pydantic import BaseModel, Field

from kikiri import DEFAULT_VOICE, VOICES, KikiriEngine

MAX_CHARS = int(os.environ.get("TTS_MAX_CHARS", "1200"))
TOKEN = os.environ.get("TTS_SERVICE_TOKEN", "")

app = FastAPI(title="Masal Dünyası TTS", docs_url=None, redoc_url=None)
engine: KikiriEngine | None = None


@app.on_event("startup")
def load() -> None:
    global engine
    engine = KikiriEngine()


def authorize(x_tts_token: str = Header(default="")) -> None:
    if TOKEN and not secrets.compare_digest(x_tts_token.encode(), TOKEN.encode()):
        raise HTTPException(status_code=401, detail="Yetkisiz")


class SpeechRequest(BaseModel):
    text: str = Field(min_length=1, max_length=MAX_CHARS)
    voice: str = DEFAULT_VOICE
    speed: float = Field(default=1.0, ge=0.5, le=1.5)
    format: Literal["mp3", "wav"] = "mp3"


@app.get("/health")
def health() -> dict:
    if engine is None:
        raise HTTPException(status_code=503, detail="Model yükleniyor")
    return {"status": "ok", "device": engine.device, "voices": list(VOICES)}


@app.post("/v1/tts", dependencies=[Depends(authorize)])
async def tts(req: SpeechRequest) -> Response:
    if engine is None:
        raise HTTPException(status_code=503, detail="Model yükleniyor")
    if req.voice not in VOICES:
        raise HTTPException(status_code=400, detail="Bilinmeyen ses")
    try:
        audio, seconds = await run_in_threadpool(engine.synthesize, req.text, req.voice, req.speed, req.format)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    media = "audio/mpeg" if req.format == "mp3" else "audio/wav"
    return Response(content=audio, media_type=media, headers={"X-Audio-Duration": f"{seconds:.2f}"})
