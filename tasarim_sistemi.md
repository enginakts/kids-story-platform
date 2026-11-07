# Masal Dünyası - Arayüz Tasarım Sistemi

Web arayüzünü ebeveynler kullanır; çocuklar yalnızca masal okuyucuyu (Adım 7) kullanacak. Bu yüzden genel arayüz sıcak ve oyuncak hissi verir ama sade ve okunaklıdır. Tüm kurallar `backend/src/main/resources/static/css/app.css` içindeki token'lardadır. Yeni sayfa yazarken ham renk veya rastgele ölçü kullanılmaz, yalnızca bu token'lar kullanılır.

## Stil

* **Yumuşak "clay" görünüm:** Büyük köşe yuvarlaklığı (16-24px), 2px kenar, katmanlı yumuşak gölge, basılınca hafifçe aşağı inen butonlar.
* **Tek ana eylem:** Her ekranda yalnızca bir birincil (mavi) buton bulunur; diğerleri ikincil ya da sade görünür.
* **İkonlar:** Emoji kullanılmaz. Tek ikon seti vardır: `fragments/layout.html` içindeki SVG sembolleri (Lucide çizgi stili, 2px). Metnin yanındaki ikon `aria-hidden="true"` alır.

## Renk

**Yalnızca açık tema vardır.** İşletim sistemi koyu temada olsa da sayfa açık kalır (`color-scheme: light`); `prefers-color-scheme` kullanılmaz. Palet pasteldir: sıcak krem zemin, lavanta, şeftali, pembe ve nane tonları. Metin çiftleri en az 4.5:1 kontrast sağlar.

| Token | Değer | Kullanım | Kontrast |
| :--- | :--- | :--- | :--- |
| `--color-bg` | `#FFF9F2` | Sayfa zemini (krem) | |
| `--color-surface` | `#FFFFFF` | Kart, form | |
| `--color-fg` | `#2D2A4A` | Ana metin | 13.0:1 (zemin) |
| `--color-muted` | `#5F5B7A` | İkincil metin | 6.2:1 (zemin) |
| `--color-primary` | `#C9BEFF` | **Yalnızca dolgu:** birincil düğme, adım numarası | |
| `--color-on-primary` | `#2E2459` | Birincil düğme yazısı (beyaz değil) | 8.1:1 |
| `--color-link` | `#5442BD` | Bağlantılar ve metin olarak kullanılan vurgu | 7.0:1 (zemin) |
| `--color-ring` | `#7A68E0` | Odak halkası, seçili kart kenarı, işaret kutusu | 4.1:1 (metin dışı) |
| `--color-primary-soft` / `--color-on-primary-soft` | `#E6E0FF` / `#3B2F7A` | Seçili menü, seçili kart | 8.8:1 |
| `--color-secondary-soft` / `--color-on-secondary-soft` | `#FFE6C2` / `#6B3F0A` | Uyarı bandı, ikon zemini | 7.4:1 |
| `--color-success-soft` / `--color-success` | `#D6F3E4` / `#1F6B45` | Onay rozeti | 5.5:1 |
| `--color-danger-soft` / `--color-danger` | `#FFDEDE` / `#A32D2D` | Silme, hata | 5.6:1 |

* **Pastel ana renk metin olmaz:** `--color-primary` metin veya bağlantı rengi olarak kullanılmaz, çünkü zeminde okunmaz; metin için `--color-link` kullanılır.
* **Renk tek başına anlam taşımaz:** Hata ve onay, renkle birlikte ikon ve metinle de belirtilir.
* **Ham renk yok:** Bileşenlerde ham renk (`#hex`, `rgb`) kullanılmaz. Ana sayfa illüstrasyonu da `--art-*` token'larıyla boyanır.

## Tipografi

* **Başlık:** Baloo 2 (500-800). **Gövde:** Nunito (400-800). İkisi de Türkçe karakterleri destekler.
* **Font dosyaları:** `static/fonts` altından, uygulamanın kendi sunucusundan verilir. Google Fonts bağlantısı kullanılmaz; böylece ziyaretçi IP'si üçüncü tarafa gitmez (KVKK).
* **Ölçüler:** Gövde 16px, satır yüksekliği 1.6. Başlık ölçeği `--text-*` token'larındadır.

## Yerleşim

* **Boşluk:** 8'lik ritim (`--space-1` ... `--space-8`). İçerik en fazla 1120px genişliktedir.
* **Mobil öncelikli:** 768px altında ana menü dört öğeli alt çubuğa iner (ikon ve etiket birlikte). Yatay kaydırma olmamalıdır; grid sütunları `minmax(0, …)` kullanır.
* **Dokunma hedefi:** Tüm butonlar ve menü öğeleri en az 44px yüksekliğindedir.

## Formlar

* **Etiket:** Her alanın görünür bir etiketi vardır; yalnızca placeholder kullanılmaz. Zorunlu alanlar `*` ile işaretlenir.
* **Hata mesajı:** İlgili alanın hemen altında gösterilir, `aria-describedby` ile alana bağlanır ve `role="alert"` taşır (`fragments/forms :: error`).
* **Onay:** Silme gibi geri alınması zor işlemler `data-confirm` ile onay ister.

## Hareket

* **Süreler:** Kısa ve tutarlı token'lar kullanılır: `--dur-fast` 120ms, `--dur` 200ms, giriş animasyonu 320ms.
* **Ne hareket eder:** Yalnızca `transform` ve `opacity` canlandırılır. Sayfa açılışında içerik hafifçe yükselir; kart üzerine gelince 2px kalkar.
* **Azaltılmış hareket:** `prefers-reduced-motion` açıksa tüm animasyonlar kapanır.

## Erişilebilirlik

* **Klavye:** Her sayfada "İçeriğe geç" bağlantısı vardır. Odak halkası görünür (3px).
* **Menü:** Seçili menü öğesi `aria-current="page"` taşır.
* **Ekran okuyucu:** Yalnızca ikondan oluşan veya bağlamı belirsiz butonlar `aria-label` alır (örn. "Ada profilini kaldır").
