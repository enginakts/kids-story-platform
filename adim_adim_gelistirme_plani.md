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
| 6 | Görsel üretimi ve dosya depolama | Vize | Sıradaki |
| 7 | TTS ve masal okuyucu | Vize | Planlandı |
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

Adım 0-5 incelendikten sonra Adım 6 ile devam edilir. Adım 6'ya başlamadan görsel üretim sağlayıcısı web'den güncel olarak araştırılıp seçilir.

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
