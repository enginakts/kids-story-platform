# Masal Dünyası ses servisi (Kikiri TTS)

Masalları Türkçe seslendiren küçük HTTP servisi. Model: [duxx/kikiri-turkish-base](https://huggingface.co/duxx/kikiri-turkish-base) (Kokoro-82M'den türetilmiş Türkçe model, tek ses: Nisan). Model ücretsizdir ve kendi bilgisayarımızda ya da ücretsiz bir Hugging Face Space'te çalışır; ücretli bir API kullanılmaz.

Spring uygulaması masal hazırlanınca her sahneyi bu servise gönderir, dönen WAV dosyasını saklar ve okuyucuda çalar.

## Yerelde çalıştırma (Windows, PowerShell)

```powershell
cd tts-service
py -3.11 -m venv .venv
.venv\Scripts\python -m pip install torch --index-url https://download.pytorch.org/whl/cpu
.venv\Scripts\python -m pip install -r requirements.txt
.venv\Scripts\python -m uvicorn app:app --port 8000
```

İlk açılışta model Hugging Face'ten indirilir (yaklaşık 330 MB) ve önbelleğe alınır. `http://localhost:8000/health` 200 dönünce servis hazırdır. Spring uygulaması varsayılan olarak `http://localhost:8000` adresini kullanır (`KIKIRI_TTS_URL` ile değiştirilir).

Servis kapalıysa masallar yine oluşur; ses işleri birkaç kez yeniden denenir, olmazsa masal sayfasında "Sesi yeniden hazırla" düğmesi çıkar.

## Hugging Face Space'e taşıma (ücretsiz)

1. Hugging Face'te **Docker** türünde, **Private** bir Space oluşturun.
2. Bu klasördeki `Dockerfile`, `requirements.txt`, `app.py`, `kikiri.py` dosyalarını Space'e yükleyin.
3. Space ayarlarında **Secrets** altına `TTS_SERVICE_TOKEN` ekleyin (uzun, rastgele bir değer). İstekler bu değeri `X-TTS-Token` başlığında taşır.
4. Spring tarafında `KIKIRI_TTS_URL=https://<kullanıcı>-<space>.hf.space`, `KIKIRI_TTS_TOKEN=<aynı değer>` ayarlayın. Space özel (private) ise `KIKIRI_TTS_HF_TOKEN` olarak okuma yetkili bir Hugging Face token'ı da verin.

Ücretsiz Space'ler bir süre kullanılmayınca uyur; ilk istek modeli yeniden yüklediği için yavaş olabilir, Spring tarafı bu durumda işi yeniden dener.

## Notlar

* **Hız:** CPU'da gerçek zamandan yaklaşık 5 kat hızlı (10 saniyelik ses ≈ 2 saniye). Bir masalın sesi arka planda, sahne sahne hazırlanır.
* **Ağırlıklar:** Checkpoint yeni PyTorch ağırlık normalizasyonu adlarıyla kaydedilmiş; Kokoro bunları sessizce atlar ve model gürültü üretir. `kikiri.py` anahtarları dönüştürüp sıkı yükler; bir ağırlık eksikse servis açılmaz.
* **Gizlilik:** Seslendirilen metin çocuğun adını içerebilir. Bu yüzden servis yalnızca bizim kontrolümüzdeki bir yerde (yerel sunucu veya bize ait özel Space) çalıştırılır; metin kaydedilmez ve log'a yazılmaz.
* **Lisans:** Model CC BY-SA 3.0 (eğitim verisi `afkfatih/turkish-tts-combined-raw`), temel model Kokoro-82M Apache 2.0. Ticari kullanım serbesttir; uygulamada modele atıf yapılmalıdır (masal okuyucusunun altında gösterilir). Üretilen ses dosyaları için de "aynı lisansla paylaş" koşulunun nasıl uygulanacağı ticari yayından önce bir hukukçuya danışılmalıdır.
