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
| 4 | Karakter ve mekân rehberleri (6 hazır karakter, 6 mekân, sabit seed) | Vize | İncelemede |
| 5 | Masal sihirbazı, üretim kuyruğu, içerik güvenliği, kredi harcama/iade, çocuğa göre kütüphane | Vize | İncelemede (gerçek LLM adaptörü bekliyor) |
| 6 | Görsel üretimi ve dosya depolama (yerel disk; bible ile tutarlı prompt) | Vize | İncelemede (gerçek görsel adaptörü ve S3/R2 bekliyor) |
| 7 | TTS ve kelime vurgulamalı masal okuyucu | Vize | İncelemede (gerçek TTS adaptörü bekliyor) |
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

## Sonraki eylem

Adım 0-3 incelendikten sonra Adım 4 ile devam edilir.

Adım 3'ten kalan bilinen sınırlar:

* Parola sıfırlanınca mobil oturumlar kapanır; ancak açık web oturumları ve verilmiş erişim token'ları (en fazla 15 dakika) sürer.
* E-posta doğrulanmamış hesaplar şimdilik engellenmez. Masal oluşturma (Adım 5) doğrulanmış e-posta isteyecek.
* Üretim için bir SMTP sağlayıcısı seçilmelidir.
