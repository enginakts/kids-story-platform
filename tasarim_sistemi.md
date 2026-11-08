# Masal Dünyası - Arayüz Tasarım Sistemi

Stil **editoryal resimli kitap**tır: büyük serif başlıklar, düz renk alanları, eski masal kitabı levhaları ve tek bir motif. Motif, Türk masallarının kapanış sözündeki **elma**dır ("Gökten üç elma düştü"). Elma; logoyu, düğmelerin yuvarlak biçimini, resim maskelerini, liste işaretlerini, avatarları ve sayfaların kapanışını taşır. Başka süs şekli kullanılmaz.

Ana sayfa ve masal okuyucu çocuğa da hitap eder ve hareketlidir. Ebeveyn ekranları (kütüphane, çocuklar, karakterler, sihirbaz) aynı palet ve fontla sakin bir araç gibi davranır. Tüm değerler `backend/src/main/resources/static/css/app.css` içindeki token'lardadır. Yeni sayfa yazarken ham renk veya rastgele ölçü kullanılmaz.

## Renk

**Yalnızca açık tema vardır** (`color-scheme: light`). Dört marka rengi ve onlardan türeyen ton adımları kullanılır.

| Token | Değer | Kullanım | Kontrast |
| :--- | :--- | :--- | :--- |
| `--ink` | `#0E0D0D` | Metin, koyu alanlar, ikincil düğme | 17:1 (kâğıt) |
| `--paper` | `#F2F0E4` | Sayfa zemini | |
| `--red` | `#F24405` | Birincil düğme, elma, vurgu alanı | Mürekkep yazı 5.2:1 |
| `--orange` | `#FF8C00` | İkinci vurgu alanı, elma | Mürekkep yazı 8.3:1 |
| `--ink-soft` | `#4A4844` | İkincil metin | 8.0:1 (kâğıt) |
| `--surface` / `--paper-2` / `--paper-3` | `#FAF9F3` / `#E6E3D4` / `#D8D4C1` | Panel, bir ve iki ton koyu yüzey | |
| `--success` / `--danger` | `#2D6A3E` / `#A8320A` | Durum metni (açık zeminleriyle) | 5.5:1 / 5.7:1 |

* **Kırmızı ve turuncu metin rengi değildir.** Kâğıt üzerinde 3.3:1 ve 2:1 kalırlar; yalnızca dolgu ve büyük yazıdır. Kırmızı veya turuncu dolgunun üzerindeki yazı her zaman mürekkeptir.
* **Koyu zeminde** (mürekkep) metin kâğıt rengidir; turuncu (8.3:1) ve kırmızı (5.2:1) burada metin olabilir.
* **Bağlantılar** mürekkep rengindedir, kırmızı alt çizgi taşır; üzerine gelince alt çizgi mürekkebe döner.
* **Renk tek başına anlam taşımaz:** hata ve onay ikon ve metinle de belirtilir.

## Tipografi

Üç yazı tipi, her birinin tek bir işi vardır. Hepsi OFL lisanslıdır, Türkçe karakterleri destekler ve `static/fonts` altından uygulamanın kendi sunucusundan verilir (KVKK: Google Fonts bağlantısı yoktur).

| Font | İş | Not |
| :--- | :--- | :--- |
| **Fraunces** | Başlıklar, kitap kapakları, büyük kelimeler | `"SOFT" 100, "WONK" 1` eksenleriyle yuvarlak ve oyuncu; 800-900 kalınlık |
| **Lora** | Masal metni, giriş paragrafları, okuma metni | Editoryal serif; sahne metni 24-34px |
| **Nunito** | Arayüz: menü, form, düğme, etiket | 17px gövde |

Ölçüler token'lardadır (`--fs-hero`, `--fs-1`, `--fs-2`, `--fs-body`, `--fs-small`). Uygulama ekranlarında başlık en fazla 32px'tir (`--fs-app-title`). Okunan hiçbir metin 15px'in altına inmez.

## Görseller

* **Masal levhaları:** `static/img/tales/` altında Virginia Albert'in 1918 tarihli *The Tale of Peter Rabbit* levhaları (kamu malı, Wikimedia Commons). Kaynakları `credits.json` dosyasındadır; ana sayfanın kapanışında ve giriş sayfalarında künye yer alır. Korkutucu sahneler (ağa takılma, kaçma) kullanılmaz.
* **Giriş sayfaları (giriş, kayıt, parola, yönetici girişi):** ekranı tam kaplar, sayfa kaymaz; ortada kırmızı kapaklı açık bir masal kitabı durur. Sol sayfa çerçeveli bir levhadır (üst başlık, künye, sayfa no 12), sağ sayfa formdur (sayfa no 13). Açılışta levha sırttan döner (`bookOpen`). Giriş ve kayıt aynı kitabın iki sayfasıdır (`auth/entry.html`, `/login` ve `/register` aynı şablonu açar): "Hesap oluştur" / "Giriş yapın" sayfayı sırttan çevirir (`bookTurn`, 0,95 sn). İleride sağ sayfa sola yatar ve arkasında yeni levha görünür, geride soldan bir yaprak sağa iner. Adres /login ⇄ /register olur, tarayıcının geri düğmesi sayfayı geri çevirir. Telefonda tek sayfa yarım döner. Hareketi azalt açıksa sayfa doğrudan değişir. Form sayfası ekrana sığmazsa `bookFit` süsleri sırayla gizler (data-fit 1-4: ipucu/üst başlık/sayfa no → açıklama → sıkı aralıklar → küçük başlık); açılışta, boyut değişince ve sayfa çevrilince yeniden ölçülür. Kitapta başlık ışık şeridi kullanılmaz. 900px altında yalnızca form sayfası görünür; kısa ekranlarda üst başlık, sayfa numarası ve açıklama metni gizlenerek form kaydırmasız sığar.
* **Elma penceresi** (`.apple-window`): resim `img/apple-mask.svg` ile elma biçiminde maskelenir, arkasında hafif kaymış kırmızı bir elma durur (baskı kayması).
* **Masal sahneleri:** görsel üretimi gelene kadar düz renk alanında büyük bir elma ve "resim bir sonraki adımda eklenecek" notu gösterilir.
* **İkonlar:** Emoji kullanılmaz. `fragments/layout.html` içinde tek SVG seti vardır: Lucide çizgi ikonları ve dolu `#apple` sembolü. Elmanın sapı ve yaprağı `--stem` ve `--leaf` değişkenleriyle renklenir.

## Yerleşim

* **Kenar boşluğu:** `--margin` (16-36px), sayfanın iki yanında eşit. Uygulama içeriği en fazla 1240px'tir.
* **Bölüm arası:** ana sayfada `--section-gap` (96-168px).
* **Biçim:** düğmeler ve sekmeler tam yuvarlak (hap), paneller 22px yarıçaplı. Gölge yoktur; yüzeyler bir ton adımıyla veya 1px kenarla ayrılır.
* **Mobil öncelikli:** 768px altında ana menü dört öğeli alt çubuğa iner. Yatay kaydırma olmamalıdır; grid sütunları `minmax(0, …)` kullanır.
* **Dokunma hedefi:** tüm düğmeler ve menü öğeleri en az 44px yüksekliğindedir.

## Formlar

* Her alanın görünür bir etiketi vardır; zorunlu alanlar `*` ile işaretlenir.
* Hata mesajı alanın hemen altında gösterilir, `aria-describedby` ile bağlanır, `role="alert"` taşır (`fragments/forms :: error`).
* Seçili seçenek kutusu mürekkep rengine dolar; odak halkası 3px mürekkep çizgisidir.
* Geri alınması zor işlemler `data-confirm` ile onay ister.

## Ana sayfa: bölüm bölüm sahneler

Ana sayfa, oyun tanıtım sitelerindeki "bölüm" düzeninden uyarlanmıştır (kod, görsel ve metin alınmadan, yalnızca teknikler):

* **Sağ bölüm menüsü** (1100px ve üstü): mürekkep zeminli, `sticky` bir sütun. Elmalı logo, altı bölüm, "Ücretsiz başla" ve "İlk 3 masal hediye" kutusu. Görünen bölüm kırmızıya dolar, üzerinden eğik bir ışık şeridi geçer. Dar ekranda bu menü gizlenir, üst bar görünür.
* **Bölüm zeminleri:** her bölüm en az bir ekran yüksekliğindedir ve zemini sert geçişle değişir: kâğıt, gece (mürekkep), kareli defter kâğıdı, turuncu, gece, kareli kâğıt, kırmızı. Masaüstünde hafif `scroll-snap` (proximity) vardır; kaydırmaya el konmaz.
* **Bölüm başlıkları:** Fraunces, 46-104px. Bölüm ekrana gelince başlığın üzerinden bir kez ışık şeridi geçer.
* **Pırıltı:** bağlantı ve düğmelerin üzerine gelince 0,3 saniyelik, bir kez oynayan yumuşak titreme (`.flash`).
* **Örnek masal:** üç masal levhası genişleyen panellerdedir (aktif panel geniş, diğerleri dikey etiketli şerit). Sağdaki listede ilerleme çizgisi 7 saniyede dolar ve sıradaki masala geçer; fare üzerindeyken veya alana odaklanınca durur, kullanıcı bir masal seçince otomatik ilerleme tamamen kapanır. Çerçevede köşe işaretleri vardır.
* **Kahramanlar:** 11 tür, farklı yükseklikte kartlarda; fareyle sürüklenir, ok düğmeleriyle kayar, "Şerit / Izgara" düğmesiyle görünüm değişir. Kart üzerine gelince elma büyüyüp sallanır, köşedeki ok döner.
* **Mekân ve tema:** üç mekân, kodla çizilmiş düz renkli editoryal illüstrasyonlu kartlarda (orman, bulut şatosu, kıyı köyü). Köşede "Hazır" kurdelesi var; üzerine gelince sahne yakınlaşır, güneş ve kayık yavaşça hareket eder. Temalar, baklava işaretli bir liste ve büyük bir gösterim panelidir: fareyle üzerine gelince önizlenir, tıklayınca seçilir (ok tuşları, Home ve End çalışır). Panelde temanın adı, açıklaması ve o temayla yazılmış örnek bir cümle görünür.
* **Sabitleme (scroll pinning):** yalnızca 1100px ve üstünde ve "hareketi azalt" kapalıyken; telefonda bölümler normal akışta kalır. Hiçbir sabitleme üç ekranı aşmaz.
  * *Nasıl çalışır:* sahne ekranda sabit kalır (bölüm 320svh). Kaydırma üç adımı sırayla oynatır: dağınık elmalar yerleşir, şerit oluşur, sahne sıraları dizilir. Etkin adım tam renkte, diğerleri %40; altta kırmızı ilerleme çubuğu vardır.
  * *Kahramanlar:* bölüm sabitlenir, aşağı kaydırma kart şeridini yatay kaydırır (bölüm yüksekliği şerit genişliğinden hesaplanır). Oklar sayfayı kaydırır. Izgara görünümünde sabitleme kapanır.
  * *Mekânlar:* kartlar yapışkandır ve üst üste yığılır; alttaki kart üzerine yenisi gelirken %94'e küçülür.
* **Kahraman resimleri:** `static/img/heroes/` altında. L. Leslie Brooke'un *Johnny Crow's Garden* (1903) levhaları (aslan, fil, kedi, ayı, tilki, baykuş, penguen), 1885 tarihli bir çocuk kitabından köpek, Joseph Smit'in İguanodon gravürü ve Virginia Albert'in tavşanı; hepsi kamu malı, kaynakları `credits.json` dosyasındadır. Robot proje için çizildi. Hepsi aynı işlemden geçer (gri ton, beyaz kâğıt) ve sayfada kartın rengiyle çarpılarak (`mix-blend-mode: multiply`) iki renkli baskı gibi görünür; mürekkep kartlarda ters çevrilir.
* **Mekân resimleri:** paletin dört rengiyle kodla çizilmiş düz editoryal illüstrasyonlardır (uygun ve tutarlı bir eski resim bulunamadı).
* **Çağrı ve kapanış:** "Başlayalım" bandının köşesinde "Hediye" kurdelesi; kapanışın altında kırmızı bağlantı şeridi (Kayıt ol, Giriş yap, Keşfet).

## Hareket

Hareket katmanı **Motion** (motion.dev, Framer Motion'ın React gerektirmeyen sürümü, 14.6.0, MIT) ile `static/js/motion.js` dosyasındadır; kütüphane `static/js/vendor` altından verilir.

* **Ana sayfa açılışı:** başlık satırları maskeden yükselir, metin ve düğme sırayla gelir, elma penceresi yaylanarak açılır. 1.2 saniyede biter.
* **İmza hareketi:** hero'daki masal levhaları elma penceresinde 5.6 saniyede bir yavaşça yer değiştirir; sekme gizliyken durur.
* **Kaydırma:** "Seçimden masala" diyagramındaki dağınık elmalar kaydırdıkça şeritte toplanıp sahne sıralarına dizilir; hero resmi hafifçe kayar.
* **Girişler:** başlık, metin, resim ve liste öğeleri bölüm görününce bir kez, 60-90ms arayla yükselir.
* **Kapanış:** üç elma gökten düşer (yaylı); üzerine gelince sallanır.
* **Geri bildirim:** düğmeler basılınca %97'ye küçülür, üzerine gelince mürekkebe döner; kitap kapakları hafifçe kalkıp eğilir.
* **Sayfa geçişi:** sayfalar arası geçişte tarayıcının View Transitions özelliği kullanılır (`@view-transition`); üst bar sabit kalır.
* **Kurallar:** yalnızca `transform`, `opacity` ve `clip-path` canlandırılır. Gizli başlangıç durumları yalnızca `html.js` altındadır; betik yüklenmezse içerik 3 saniye sonra görünür olur. `prefers-reduced-motion` açıksa yolculuk, ölçek ve döngüler kapanır; sayfa bitmiş hâliyle görünür.

## Erişilebilirlik

* Her sayfada "İçeriğe geç" bağlantısı vardır; odak halkası görünür (3px).
* Seçili menü öğesi `aria-current="page"` taşır.
* Ana sayfadaki örnek masal sekmeleri `role="tablist"` kullanır ve ok tuşlarıyla gezilir; girilen takma ad hiçbir yere gönderilmez.
* Yalnızca ikondan oluşan veya bağlamı belirsiz düğmeler `aria-label` alır.
