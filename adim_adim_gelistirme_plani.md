# Masal Dünyası AI - Geliştirme Yol Haritası

Proje küçük, test edilebilir adımlarla ilerler. Her adım hem web arayüzünü (Thymeleaf) hem mobil REST API'yi kapsar. Bir adım test edilip kullanıcı onayı alınmadan sonrakine geçilmez.

## Kapsam

* **Vize:** Kayıt, çocuk profili, masal oluşturma, sahneler, görsel, TTS, kütüphane, favori, özel/herkese açık masal, keşfet, yönetim paneli.
* **Final:** Masal sonrası sohbet asistanı ve masal ortasında iki seçenekli devam.

## İlerleme

| Adım | Modül | Dönem | Durum |
| :---: | :--- | :---: | :---: |
| 0 | Proje iskeleti, 29 tablo, güvenlik, testler | Vize | İncelemede |
| 1 | Kimlik doğrulama: kayıt, giriş, ayrı yönetici girişi | Vize | İncelemede |
| 2 | Çocuk profilleri ve veri izolasyonu | Vize | İncelemede |
| 3 | Hesap tamamlama: e-posta doğrulama, parola sıfırlama, mobil yenileme token'ı, giriş hız sınırı | Vize | İncelemede |
| 4 | Karakter rehberi (ebeveyne ait) ve sistem mekân şablonları; arayüz tasarım sistemi | Vize | İncelemede |
| 5 | Masal sihirbazı; Trendyol LLM ile metin, Gemini Flash ile sahnelere bölme; içerik güvenliği, kredi | Vize | İncelemede |
| 6 | Görsel üretimi (Cloudflare FLUX.2 klein), karakter referans portreleri ve dosya depolama | Vize | İncelemede |
| 7 | TTS (Kikiri, ücretsiz Türkçe model) ve masal okuyucu | Vize | İncelemede |
| 8 | Kütüphane, favoriler ve ebeveyn kilidi | Vize | Planlandı |
| 9 | Özel/herkese açık masal, keşfet, denetim kuyruğu | Vize | Planlandı |
| 10 | Yönetim paneli: kullanıcı, masal, kara liste, prompt, sağlayıcı yönetimi | Vize | Planlandı |
| 11 | Etkileşimli masal: iki seçenekli devam | Final | Planlandı |
| 12 | Masal sonrası sohbet asistanı | Final | Planlandı |
| 13 | Abonelik ve ödeme | Ticari | Planlandı |
| 14 | Mobil uygulama | Sonrası | Planlandı |

## Adım kuralları

* Yapay zekâ sağlayıcısı, kütüphane ve fiyat kararları kullanılacağı adımda web'den güncel olarak doğrulanır.
* Ücretsiz katmanı olan sağlayıcılar önceliklidir; ancak ticari kullanım ve çocuk verisi koşulları ayrıca kontrol edilir.
* Dış servislere çocuğun adı veya kişisel bilgisi gönderilmez; gerekirse takma ad ya da karakter adı kullanılır.
* Her LLM çıktısı kullanıcıya ulaşmadan önce şema ve içerik güvenliği denetiminden geçer.
* Çocuğun adını içeren kişiselleştirilmiş masal keşfette yayımlanmadan önce addan arındırılır.
* Etkileşimli masalda `interactive_nodes.selected_choice` tek bir okumayı tutar. Herkese açık etkileşimli masal istenirse seçimler okuma seansına taşınır.

## Adım 4 kararları

* **Karakterler ebeveyne bağlıdır** (`character_bibles.parent_id`). Kardeşler aynı karakteri kullanabilir; masallar yine tek çocuğa bağlı kalır.
* **Mekânlar yalnızca sistem şablonlarıdır.** Ebeveyn mekân oluşturmaz, listeden seçer. Şema değişmez.
* **Görsel alanlar ebeveynden alınmaz.** Ebeveyn tür, renk, kıyafet ve aksesuarı Türkçe listelerden seçer; `visual_anchor_tokens` sistem tarafından şablonla üretilir. Serbest metin yalnızca ad ve kişilik alanındadır ve kara listeden geçer.
* **Karakter adı çocuğun adını içeremez.** Ebeveynin çocuklarının adlarıyla karşılaştırılır, eşleşirse reddedilir.
* **Silme pasife almadır.** `character_bibles.is_active` eklenir (V4).
* `voice_id`, `base_seed` ve `reference_image_url` alanları Adım 6-7'ye kadar boş kalır, arayüzde gösterilmez.
* Sistem şablonlarının yönetimi Adım 10'a aittir. Ebeveyn başına karakter sınırı `system_settings` içinden okunur.
* Kapsam: V4 migration, `/api/v1/characters` ve `/api/v1/locations`, `/app/characters` sayfaları, veri izolasyonu testleri.

## Sonraki eylem

Adım 6 ve 7 incelendikten sonra Adım 8 (kütüphane, favoriler ve ebeveyn kilidi) ile devam edilir. Proje sahibi ses adımını görselden önce aldı.

## Karakter ve mekân kütüphanesi yönetime taşındı (V10)

* **Karar (proje sahibi):** Ebeveynler karakter ve mekân oluşturamaz, değiştiremez. Karakter ve mekânları yöneticiler yönetim panelinden (`/admin/characters`, `/admin/locations`) ekler, düzenler, yayından kaldırır; yayındakiler bütün ebeveynlerin masal sihirbazında görünür. Her işlem `admin_audit_logs` tablosuna yazılır.
* **Ebeveyn tarafı:** `/app/characters` sayfası, menü bağlantısı ve `POST/PUT/DELETE /api/v1/characters` kaldırıldı; `GET /api/v1/characters` ve `/api/v1/locations` yalnızca yayındaki kayıtları listeler. Masal oluşturma yalnızca yayındaki yönetici karakter ve mekânlarını kabul eder.
* **Eski kayıtlar:** V10 öncesinde ebeveynlerin oluşturduğu karakterler silinmedi (eski masallar bağlı) ama yeni masalda seçilemez. Kayıtlar silinmez, yayından kaldırılır (`location_bibles.is_active` eklendi).
* **Çevre bilgisi resme gider:** Mekânın İngilizce resim tarifi, ışığı ve sabit öğeleri her sahne resminin isteğine (`Setting: …; lighting: …; recurring landmarks: …`) ve sahneleri bölen modele eklenir. Türkçe ad, ortam türü ve atmosfer ebeveyne görünür ve masal metni prompt'una gider. Değişiklikler yalnızca yeni masalları etkiler (sahne resim isteği masal oluşurken kaydedilir).
* **Karakter görünümü:** Yönetici görünümü katalogdan seçer (tür, renk, kıyafet, aksesuar); İngilizce tarif otomatik üretilir. Görünüm değişirse karakterin referans portresi sıfırlanır ve bir sonraki resimde yeniden çizilir.
* **Örnek resimler (V11, `library_images`):** Yeni karakter ve mekân, liste sayfasındaki "Yeni … oluştur" düğmesiyle sayfa içinde açılan formdan eklenir; en az 1, en fazla 4 örnek resim (JPEG/PNG, resim başına 5 MB) zorunludur. Resimler 1200 piksele küçültülüp JPEG olarak yeniden kodlanır (gömülü konum gibi bilgiler silinir), `/library/images/{id}` adresinden oturumsuz sunulur ve ebeveyn sihirbazında kapak olarak gösterilir. Düzenlemede resim eklenip kaldırılabilir, son resim kaldırılamaz. Örnek resimler yalnızca ebeveyne gösterilir; sahne resmi üretimine referans olarak gitmez.

## Metin sağlayıcısı değişikliği (V9)

* Proje sahibinin kararıyla masal metni artık **Groq** üzerinden yazılır (`openai/gpt-oss-120b`, Groq'un model listesinden 9 Kasım 2026'da doğrulandı). Featherless (Trendyol LLM) silinmedi; 2. sıradadır ve anahtarı verilirse Groq hata verdiğinde yedek olur. Sahnelere bölme Gemini'de kalır.
* Aynı metin prompt'u (`story.text.v3`) kullanılır; Qwen'e özgü `/no_think` komutu Groq'a gönderilmez. Çocuk verisi kuralı değişmez: Groq'a çocuğun adı gitmez, yer tutucu ad kullanılır.
* Groq anahtarı varken taslak metin yüklenmez; Groq hata verirse iş yeniden denenir. Testler `backend/.env` dosyasındaki gerçek anahtarları kullanmaz.

## Adım 6 kararları (Cloudflare FLUX.2 klein ile sahne resimleri)

* **Sağlayıcı (proje sahibinin kararı):** Cloudflare Workers AI üzerinde Black Forest Labs **FLUX.2 [klein] 4B** (açık ağırlıklar Apache 2.0). Günde 10.000 ücretsiz "neuron" verilir; 1024x768 bir resim yaklaşık 100 neuron tutar, yani günde yaklaşık 90-100 resim (20 civarı masal) ücretsizdir. Aşımı 1.000 neuron başına 0,011 $'dır (resim başına yaklaşık 0,001 $). Cloudflare bu model için veri saklamaz (zero data retention).
* **Araştırılan diğer seçenekler:** Gemini görsel modelleri (Nano Banana) ücretsiz katman sunmuyor (resim başına yaklaşık 0,034 $). Kendi GPU'muzda FLUX.2 klein ücretsiz ama 8 GB kartta sınırda ve canlıda GPU'lu sunucu ister. Model adı `ai_providers_config` tablosundan değiştirilebilir; `@cf/black-forest-labs/flux-1-schnell` daha ucuzdur ama boyut ve referans görsel almaz (adaptör ikisini de destekler).
* **Karakter tutarlılığı:** Her karakter için bir kez beyaz zeminli bir referans portre üretilir ve `character_bibles.reference_image_url` alanına kaydedilir (tohum `base_seed`). Sonraki tüm sahnelere bu portreler referans görsel olarak verilir (en fazla 3 karakter, 448 piksele küçültülmüş). İkinci masalda aynı karakterin portresi yeniden üretilmez. Sahne resmi başarısız olsa bile üretilen portre saklanır. Aynı masalın sahneleri birbirine yakın tohumlarla çizilir.
* **Akış:** Masal metni hazır olunca her sahne için bir `SCENE_IMAGE` işi kuyruğa girer. Kuyruk önceliği: masal metni, sonra resimler, sonra sesler. Masal resimler beklenmeden okunabilir; okuyucu hazır olan resimleri sayfa yenilenmeden gösterir. 1. sahnenin resmi masalın kapağıdır (`stories.cover_image_url`) ve kütüphanede gösterilir.
* **Hata:** Resim işi 3 kez denenir; günlük ücretsiz kota bitmişse (HTTP 429) `retry-after` kadar beklenir. Olmazsa masal geçerli kalır, kredi iade edilmez; ebeveyn "Resimleri yeniden çiz" ile yalnızca eksik sahneleri yeniden ister.
* **Çocuk verisi ve güvenlik:** Görsel prompt'una çocuğun adı gitmez (yer tutucu "a small child" olur); yalnızca İngilizce sahne tarifi, karakter ve mekân görünüşleri ve sabit çizim tarzı gider. Resimler herkese açık adresten değil, sahiplik denetimi yapan uç noktalardan sunulur. Üretilen resimler için otomatik görsel içerik denetimi yoktur: prompt'lar kara listeden geçmiş metinden ve güvenli sabit tarzdan oluşur, resimleri yalnızca ebeveyn görür. Keşfette yayımlanmadan önce (Adım 9) resimler de yönetici denetiminden geçmelidir.
* **Geliştirme:** Cloudflare anahtarı yoksa dış servise gidilmez; prompt'a göre renklenen düz bir taslak resim üretilir.
* **Açık konular:** Gerçek Cloudflare çağrısı henüz denenmedi (hesap bilgileri bekleniyor); karakter tutarlılığının ve çizim tarzının kalitesi gerçek resimlerle değerlendirilmeli. Yerel dosya deposu tek sunucu içindir; bulut adımında S3 uyumlu bir depoya geçilmeli.

## Adım 7 kararları (Kikiri TTS ve masal okuyucu)

* **Model:** Proje sahibinin kararıyla Hugging Face'teki ücretsiz **Kikiri Türkçe TTS** (`duxx/kikiri-turkish-base`, Kokoro-82M'den türetilmiş, tek ses: Nisan). Model için Hugging Face'te hazır bir çalıştırma servisi yoktur; bu yüzden kendi küçük servisimizde çalışır (`tts-service/`, FastAPI). Servis yerelde veya ücretsiz, özel (private) bir Hugging Face Space'te aynı Dockerfile ile çalışır.
* **Neden ayrı servis:** Model Python (PyTorch) gerektirir. Spring uygulaması servise `KikiriTtsClient` ile HTTP üzerinden bağlanır (`ai_providers_config` içinde `TTS` / `kikiri`). Başka bir TTS sağlayıcısına geçmek için yeni bir adaptör ve tablo satırı yeterlidir.
* **Teknik ayrıntı:** Metin eSpeak-ng ile Türkçe fonemlere çevrilip modele verilir (model böyle eğitildi). Checkpoint yeni PyTorch ağırlık normalizasyonu adlarıyla kaydedilmiş; Kokoro bunları sessizce atlayıp gürültü üretiyordu. Servis anahtarları dönüştürüp sıkı yükler; eksik ağırlık olursa açılmaz. Çıktı 24 kHz MP3'tür (5 saniyelik sahne yaklaşık 38 KB). CPU'da 10 saniyelik ses yaklaşık 2 saniyede üretilir.
* **Akış:** Masal metni hazır olunca masal hemen okunabilir ("Hazır") ve her sahne için bir `SCENE_AUDIO` işi kuyruğa girer. Aynı veritabanı kuyruğu kullanılır; masal metni işleri her zaman önce alınır, ses işleri yeni masalları bekletmez. Okuyucu ses durumunu 4 saniyede bir yoklar ve hazır olan sahneleri sayfa yenilenmeden açar.
* **Hata:** Ses işi 3 kez denenir (servis uyuyorsa veya meşgulse `retry-after` kadar beklenir). Olmazsa masal geçerli kalır, kredi iade edilmez; ebeveyn "Sesi yeniden hazırla" ile yalnızca eksik sahneleri yeniden ister. Ses servisi kapalıyken uygulama ve masal üretimi çalışmaya devam eder.
* **Anlatım hızı:** Uyku masalı 0,88, sıcak ve kafiyeli 0,95, oyunbaz 1,0. Karakter başına ses (`voice_id`) modelde tek ses olduğu için kullanılmıyor.
* **Dosyalar:** Ses dosyaları `MediaStorage` ile saklanır (şimdilik sunucu diski, `MEDIA_DIR`). Dosyalar herkese açık bir adresten değil, sahiplik denetimi yapan uç noktalardan sunulur (web: oturum, mobil: token). Tarayıcının parça parça istemesi (Range) desteklenir.
* **Çocuk verisi:** Seslendirilen metin kişiselleştirilmiş olabilir (çocuğun adını içerir). Bu yüzden servis üçüncü taraf bir API değil, bizim çalıştırdığımız bir sunucudur; metin kaydedilmez ve log'a yazılmaz. Servis internete açıksa `TTS_SERVICE_TOKEN` zorunlu tutulur.
* **Lisans:** Model CC BY-SA 3.0, temel model Apache 2.0. Okuyucuda atıf satırı gösterilir. "Aynı lisansla paylaş" koşulunun üretilen ses dosyalarına nasıl uygulanacağı ticari yayından önce bir hukukçuya danışılmalıdır.
* **Okuyucu:** Betik yoksa sahneler alt alta, her biri kendi ses oynatıcısıyla gösterilir. Betik varsa sayfa sayfa okunur: önceki/sonraki, sahne noktaları, klavye okları, "Baştan dinle" ve sahne bitince kendiliğinden sonraki sahneye geçme. "Hareketi azalt" açıksa geçişler canlandırılmaz.
* **Açık konular:** Ses kalitesi ve Türkçe telaffuz insan kulağıyla değerlendirilmeli (otomatik kontrolde sinyal, modelin kendi örnek kaydıyla aynı özellikleri gösteriyor). Masal sayfası ve sesler ebeveyn oturumunda açılır; çocuğun ebeveyn kilidiyle kendi başına dinlemesi Adım 8'e aittir. Ücretsiz Space uykudan uyanırken ilk ses gecikebilir.

## Adım 5 kararları (Trendyol LLM + Gemini Flash)

Masal iki aşamada üretilir:

1. **Metin: Trendyol LLM** (`Trendyol/Trendyol-LLM-8B-T1`, Qwen3-8B tabanlı, Apache-2.0), **Featherless AI** üzerinden (OpenAI uyumlu API). Trendyol'un kendi API'si yoktur. Model bir akıl yürütme modelidir; düşünme çıktısı prompt sonundaki `/no_think` ile kapatılır, yine gelirse `<think>` bloğu atılır. JSON şeması desteği dokümante edilmediği için düz metin istenir (ilk satır başlık, sonra paragraflar).
2. **Sahnelere bölme: Google Gemini Flash** (`gemini-3.6-flash`, kararlı), Google'ın varsayılan Interactions API'si üzerinden. Gemini, Trendyol'un metnini değiştirmeden 4-6 sahneye böler ve her sahne için İngilizce görsel tarifi yazar. Yanıt JSON şemasına uydurulur; `store: false` gönderilir, düşünme `low` seviyededir.

* **Anahtarlar:** `FEATHERLESS_API_KEY` ve `GEMINI_API_KEY`. Geliştirmede bir anahtar yoksa o aşama için taslak kullanılır; üretimde ikisi de zorunludur. Model adları `ai_providers_config` tablosundan değiştirilebilir (`LLM` = metin, `SCENE` = sahneler).
* **Groq:** Kod korunur, `ai_providers_config` içinde pasiftir; yönetimden yeniden açılabilir.
* **Çocuk verisi:** Çocuğun adı hiçbir sağlayıcıya gönderilmez. Trendyol yer tutucu bir ad kullanır (Kiko vb.); masal kaydedilirken gerçek adla ve doğru Türkçe hal ekiyle değiştirilir ("Kiko'nun" → "Ada'nın"). Trendyol'a yalnızca yaş ve ilgi alanları gider. Gemini'ye yalnızca masal metni ile karakter ve mekân görünüşleri gider; ilgi alanları gitmez.
* **Güvenlik:** Sihirbazda serbest metin yoktur. Trendyol'un metni Gemini'ye gitmeden önce kara listeden geçer. Gemini çıktısı şema (4-6 sahne, uzunluk) ve kara liste denetiminden geçer. Herhangi bir aşama başarısız olursa iş yeniden denenir; 3 denemede başarısız olursa masal "oluşturulamadı" olur ve kredi iade edilir.
* **Hatalar:** 429 yanıtında `retry-after` (yoksa 30 sn) beklenir. Featherless'ta soğuk model (400) ve kapasite yetersizliği (503) yeniden denenir.
* **Veri politikaları:** Featherless, prompt ve yanıtları kaydetmediğini belirtir. Gemini ücretsiz katmanında içerik Google ürünlerini geliştirmek için kullanılır ve insanlar tarafından okunabilir; ücretli katmanda kullanılmaz. Ticari kullanımda ücretli katman önerilir.
* **Gemini yaş koşulu (karar kaydı):** Gemini API koşulları, API'nin 18 yaş altına yönelik veya onların erişmesi muhtemel uygulamalarda kullanılmasını yasaklar. Proje sahibinin kararı: Gemini'yi yalnızca ebeveyn kullanır; masal oluşturma ve tüm ayarlar ebeveyn hesabındadır. Ticari yayından önce bu yorum, özellikle çocuğun kullanacağı okuyucu (Adım 7) ve sohbet asistanı (Adım 12) devreye girdiğinde, yeniden değerlendirilmelidir.
* **E-posta doğrulaması:** Masal oluşturmak için zorunludur.
* **Durum:** Görsel (Adım 6) ve ses (Adım 7) eklenene kadar metin hazır olduğunda masal "Hazır" sayılır.
* **Açık konu:** Kurallar her sahneyi en fazla 2 cümle ile sınırlıyor. Bu yüzden "Uzun (6 sahne)" masal sesli okunduğunda hedeflenen 4 dakikadan kısa sürebilir; süre TTS adımında ölçülüp gerekirse kural veya hedef süre güncellenmeli.

Adım 4'te uygulanan ek kurallar:

* Kara liste eşleşmesi: 4 harf ve üzeri kelimeler kelime başında eşleşir ("canavarlar" yakalanır); daha kısa kelimeler yalnızca tam kelime olarak eşleşir ("kan" kara listede olsa da "kanat" engellenmez).
* Karakter adı, ebeveynin aktif çocuklarından birinin adını tam kelime olarak içeremez ("İpek" varken "Tavşan İpek" reddedilir, "İpekböceği" kabul edilir).

Adım 3'ten kalan bilinen sınırlar:

* Parola sıfırlanınca mobil oturumlar kapanır; ancak açık web oturumları ve verilmiş erişim token'ları (en fazla 15 dakika) sürer.
* E-posta doğrulanmamış hesaplar şimdilik engellenmez. Masal oluşturma (Adım 5) doğrulanmış e-posta isteyecek.
* Üretim için bir SMTP sağlayıcısı seçilmelidir.
