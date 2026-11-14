# Yeni görevler

Kaynak: 14.11.2026 tarihli arayüz, akış ve güvenlik denetimi (çalışan uygulama, `dev` profili, commit `e706969` ve karakter/mekân kütüphanesinin yönetime taşındığı V10 değişikliği). Dosya:satır referansları denetim anındaki koda göredir; düzeltirken satırlar kaymış olabilir.

Önem: **Yüksek** (yasal ya da akışı bozan) · **Orta** · **Düşük**. Zorluk: Küçük / Orta / Büyük.
Her görev bitince `[x]` ile işaretlenir.

---

## 1. Hatalar ve eksikler

### Yüksek

- [ ] **Kayıt formu onay kutusu hatası.** Kutu işaretlenmeden gönderilince yalnızca İngilizce Spring hatası çıkıyor ("Failed to convert value of type 'null' to required type 'boolean'…"). E-posta ve parola hataları gizleniyor; telefonda metin formu yana taşırıyor.
  - Neden: form `novalidate`, işaretsiz kutu `consent` parametresini hiç göndermiyor, record'daki ilkel `boolean` alana `null` bağlanamıyor (`auth/entry.html`, `dto/ParentRegisterRequest.java`, `AuthPageController.java`).
  - Yapılacak: `_consent` gizli alanı ekle ya da alanı `Boolean` + `@AssertTrue`/`@NotNull` yap; `messages.properties` içine Türkçe `typeMismatch` mesajları ekle (çocuk yaşı alanı da aynı sorundan etkileniyor).
- [ ] **Çocuk profilindeki serbest metin denetimsiz yapay zekâya gidiyor.** "Sevdiği hayvanlar", "renkler", "ilgi alanları" (her biri 500 karakter) kara listeden geçmeden Groq'a gönderiliyor (`StoryPromptBuilder.interests()`, `ChildProfileService.apply()`). Proje kuralı md. 1'e aykırı; kişisel bilgi sızabilir ve prompt enjeksiyonuna açık.
  - Yapılacak: alanları listeden seçilen etiketlere çevir (karakterlerdeki gibi) ya da en azından kara liste + çocuk adı denetimi uygula ve uzunluğu ~60 karaktere indir.
- [ ] **KVKK aydınlatma metni yok.** Kayıttaki onay cümlesi metne bağlanmıyor, sayfa da yok. Herkese açık aydınlatma metni ve gizlilik sayfası ekle; `ParentConsent` sürümünü metnin sürümüyle eşle.
- [ ] **Hesap ve veri hakları yok.** Hesap ayarları sayfası (ad, e-posta, parola değiştirme), hesap silme, veri indirme, kredi ve onay geçmişi yok. Çocuk "Kaldır" ile yalnızca pasife alınıyor ve arayüz bunu söylemiyor (`ChildProfileService.java`). Kalıcı silme yolu ekle (bkz. özellik E9).

### Orta

- [ ] **Kişiselleştirmede ünlü uyumu hatası.** "Defne **da** yanlarına katıldı" çıktı; `ChildNamePersonalizer.apply()` yalnızca kesme işaretinden sonraki ekleri çekimliyor. Ayrı yazılan `da/de`, `mı/mi/mu/mü`, `ki` de çekimlensin; testlere ekle.
- [ ] **Hesap kilidi hizmet engellemeye açık.** Kilit yalnızca giriş adına bağlı (5 hatalı deneme → 15 dk); başkası bir ebeveyni dışarıda bırakabilir (`LoginAttemptService.java`). Giriş adı + IP birleşimi ya da artan gecikme/CAPTCHA kullan.
- [ ] **Askıya alma açık oturumu kapatmıyor** (`AdminService.setParentActive`). Askıya alınan ebeveynin web oturumu süresi dolana kadar açık kalıyor. Ayrıca askıya alınmış hesap "E-posta veya parola hatalı" mesajı alıyor; ayrı mesaj ver (`SecurityConfig`, `DisabledException`).
- [ ] **"Son giriş" hiç yazılmıyor.** `Admin.lastLoginAt` ve `Parent.lastLoginAt` hiçbir yerde güncellenmiyor; Yöneticiler sayfasında hep "—".
- [ ] **"Onay bekleyen" sayacı ve filtresi ölü.** Hiçbir kod masalı `PENDING` yapmıyor. Keşfet (Adım 9) gelene kadar gizle ya da akışı bağla.
- [ ] **Moderasyon ekranı zayıf** (`admin/story.html`, `AdminService.moderate`):
  - sahne resimleri ve ses gösterilmiyor;
  - onay formu başarısız ya da istenmemiş masallarda da çıkıyor (başarısız masal "onaylanabiliyor");
  - önceki kararlar ve gerekçeler listelenmiyor.
- [ ] **Yönetici ekranlarında çocuk verisi açık.** Masal listesi ve ayrıntısı her yöneticiye çocuğun gerçek adını ve ebeveynin e-postasını gösteriyor. Adı maskele ("D***"), e-postayı yalnızca süper yöneticiye göster.
- [ ] **Ebeveynler tablosu telefonda kesiliyor.** Kredi, durum ve işlem düğmeleri ekran dışında (`admin/parents.html`). Dar ekranda satırları karta çevir (mekân listesinde yapıldığı gibi).
- [ ] **Kredi eklemede onay ve gerekçe yok.** Aynı dakikada iki kez "+3 kredi" kaydı görüldü (olası çift gönderim). Onay adımı, gerekçe alanı ve çift gönderim koruması ekle.
- [ ] **Telefonda yönetici menüsünde kaydırma ipucu yok.** Süper yöneticide 7 öğe var; "Yapay zekâ" ve "Yöneticiler" ekran dışında kalıyor ve kaydırılabildiği anlaşılmıyor. Kenarda solma/gölge ipucu ekle ya da 4 öğe + "Diğer" menüsü kullan.
- [ ] **Mekânın İngilizce resim alanları fiilen denetimsiz.** Kara listede yalnızca 5 Türkçe kelime var (canavar, korkunç, kan, öldür, terk); "dark scary forest, blood" gibi bir tarif geçer ve bütün yeni sahne resimlerine eklenir. İngilizce kara liste ekle.
- [ ] **Kütüphane değişiklikleri geri alınamıyor.** Mekân/karakter düzenlemesinde eski değerler kaydedilmiyor, önizleme yok; tek hata bütün yeni masalların resimlerini etkiliyor (`CharacterService.updateLocation`). Denetim kaydına eski değerleri yaz (bkz. özellik Y10).
- [ ] **Referans portre yöneticiye görünmüyor.** Karakter kartlarında kamu malı gravür çıkıyor; sahnelere giden yapay zekâ portresi görülemiyor ve yeniden çizilemiyor. Portreyi göster, "portreyi yeniden çiz" düğmesi ekle.
- [ ] **Ebeveynlerin eski karakterleri sessizce kayboldu.** V10 öncesi ebeveyn karakterleri (örn. "Pıtırcık") sihirbazdan çıktı, yönetim listesinde de yok. Ebeveyne tek seferlik bilgilendirme şeridi göster; yöneticiye bu karakterleri kütüphaneye taşıma seçeneği ekle (bkz. Y11).
- [ ] **Okuyucuda ebeveyn kapısı olmadan dış bağlantı.** Hugging Face atfı `target=_blank` bağlantı olarak çocuk ekranında (`app/story.html`). Kural md. 5'e aykırı; atfı ebeveyn alanına (lisanslar sayfası) taşı ya da düz metin yap.
- [ ] **Sahne resimlerinin `alt` metni boş** (`app/story.html`). Sahne metni ya da kısa bir Türkçe tarif kullan.
- [ ] **Kütüphane kapağı tam boy resim yüklüyor** (1024×768, ~568 KB). Resim kaydedilirken ~400 px WebP küçük resim üret.
- [ ] **Var olmayan özellik vaadi.** Kayıtta "3 masal ve 1 etkileşimli masal hediye" yazıyor, kütüphanede etkileşimli kredi görünüyor; özellik henüz yok (Adım 11). Gizle ya da "yakında" diye işaretle.
- [ ] **Sihirbazda hata deneyimi.** Hata sonrası üstte özet yok, odak ilk hataya gitmiyor, sayfa `/app/stories` adresinde kalıp yenilemede yeniden gönderme uyarısı veriyor. Tek çocuk varsa ön seçili gelmiyor.

### Düşük

- [ ] Ana sayfa "Serbest metin yazılmaz" diyor (`index.html`), oysa çocuk profilinde serbest metin var; vaadi ya da alanları düzelt.
- [ ] Kayıt, var olan e-postayı ele veriyor ("Bu e-posta adresi zaten kayıtlı"); parola sıfırlamadaki gibi nötr mesaj ver.
- [ ] E-posta doğrulama token'ı GET ile tüketiliyor; e-posta tarayıcıları önceden açabilir. GET onay sayfası göstersin, doğrulama POST ile yapılsın.
- [ ] Doğrulama başarı sayfasında `<h1>` yok (`auth/verify-result.html`).
- [ ] Telefonda alt menü etiketleri 13 px (tasarım sistemi en az 15 px istiyor).
- [ ] Bazı bağlantılar ve onay kutuları 22-23 px yüksekliğinde (44 px dokunma hedefi kuralı).
- [ ] Çocuk adı normalize edilmiyor ("dert" küçük harfle kalıyor); baş harfi Türkçe yerelle büyüt.
- [ ] Başka ebeveynin çocuğuna geçersiz form POST'u 404 yerine 200 ile düzenleme sayfası çiziyor (veri sızmıyor) (`ChildPageController`).
- [ ] Yan karakter listesi ana karakteri de içeriyor; ana + 2 yan seçilip ana yan olarak da işaretlenirse gereksiz "En fazla 2" hatası çıkıyor (`StoryRequest`).
- [ ] Sihirbazda seçili seçenek kırmızı; tasarım dokümanı mürekkep rengi diyor. Kodu ya da dokümanı eşitle.
- [ ] Masal hazırlanırken okuyucu sayfası 3 sn'de bir tamamen yenileniyor; var olan JSON durum ucunu kullan.
- [ ] Ses 0/4 hazırken "Baştan dinle" düğmesi etkin.
- [ ] Masal kartında oluşturma tarihi yok; aynı başlıklı masallar ayırt edilemiyor.
- [ ] Tek tip hata sayfası (400/403/404 aynı metin), kart ortalanmamış; CSRF süresi dolunca oturumun bittiği söylenmiyor; giriş yapmış kullanıcıyı Kütüphanem'e yönlendir.
- [ ] Güvenlik başlıkları: `Content-Security-Policy`, `Referrer-Policy` ve oturum çerezinde `SameSite` ekle.
- [ ] Yapay zekâ sayfasında "stub" satırları "Açık" görünüyor; model adı ve zaman aşımı panelden değiştirilemiyor, sağlayıcı test düğmesi yok.
- [ ] Masal listesi 100 kayıtla sınırlı; sayfalama, arama, sağlayıcı adı ve hata nedeni yok.
- [ ] Yönetici parolası sıfırlama, rol değiştirme, 2FA ve ilk girişte parola değiştirme yok.
- [ ] Dev seed'ine yalnızca `ROLE_ADMIN` olan bir hesap ekle (rol ayrımı canlı test edilemedi).
- [ ] Ortak karakter adı bir çocuğun adıyla aynı olabiliyor (örn. "Ece"); yaygın çocuk adlarıyla karşılaştır ya da uyar.
- [ ] Telefonda yönetici karakter kartları çok uzun; küçük resimli liste görünümü ekle.
- [ ] Yönetici girişindeki "Bilgiler hatalı." mesajını ebeveyn tarafındaki üsluba uydur.
- [ ] Konsolda View Transition "Transition was aborted" uyarısı (zararsız gürültü).
- [ ] Tasarım dokümanındaki "Keşfet" bağlantısı ve seçili seçenek rengi güncel değil.

### Yapıldı

- [x] Telefonda yönetici menüsünde "Yapay zekâ" bağlantısı yoktu → eklendi (14.11.2026).
- [x] Mekân listesi masaüstünde dar, telefonda düğmeleri ekran dışındaydı → tablo kartlara çevrildi (14.11.2026).

### Denenecekler (henüz doğrulanmadı)

- [ ] V10'dan sonra gerçek bir masal oluşturup sahne resmi isteğinde mekânın ışık ve sabit öğelerinin gittiğini ve resimleri iyileştirip iyileştirmediğini canlı kontrol et.
- [ ] Yönetim kütüphanesinde gerçek bir karakter ve mekân ekleme, düzenleme, yayından kaldırma akışını canlı dene.
- [ ] Yalnızca `ROLE_ADMIN` hesabıyla `/admin/ai` ve `/admin/admins` yasağını canlı dene.
- [ ] Ekran okuyucu ve kontrast ölçümü.

---

## 2. Yeni özellik önerileri

### Ebeveyn tarafı

| # | Özellik | Neden | Zorluk | Planda? |
|---|---|---|---|---|
| E1 | **Uyku modu / tam ekran okuyucu:** menüsüz, karartılmış, büyük düğmeli; ekranı açık tutar (Wake Lock); bitince "İyi geceler" kapanışı | Asıl kullanım anı yatakta telefonla okumak; şu an mobilde okuyucu kaydırma istiyor | Küçük-Orta | Kısmen (Adım 7) |
| E2 | **Ebeveyn kilidi ve "çocuk dinliyor" modu:** çocuk yalnızca kütüphane ve okuyucuyu görür; çıkış, silme, masal oluşturma ve dış bağlantılar matematik sorusunun arkasında | Kural md. 5 | Orta | Evet (Adım 8) |
| E3 | **Favoriler, çocuğa göre filtre, "bu geceki masal" ve tekrar dinle** | 3-7 yaş aynı masalı defalarca ister | Küçük | Evet (Adım 8) |
| E4 | **Uyku saati hatırlatıcısı:** profildeki `bedtimeHour` ile saatten 15 dk önce e-posta/push | Var olan veri; alışkanlık oluşturur | Küçük-Orta | Hayır |
| E5 | **Çocuk görünüşü (fotoğrafsız):** saç rengi/uzunluğu, ten tonu, gözlük listeden seçilir, resim isteğine "a small child with…" olarak gider | Şu an her çocuk aynı varsayılan çocuk olarak çiziliyor | Orta | Hayır |
| E6 | **Kelime vurgulu sesli okuma (karaoke)** | Kural md. 4; okuma öncesi beceri | Orta-Büyük | Evet (kural md. 4) |
| E7 | **Masal sonrası "konuşalım" kartı:** 2-3 ebeveyn sorusu | Duygusal gelişim; sohbet asistanına (Adım 12) ara adım | Küçük | Kısmen |
| E8 | **Seri / devam masalı:** aynı karakterlerle "ikinci gece" | Referans portreler zaten var | Orta | Hayır |
| E9 | **Hesap ve gizlilik merkezi:** parola/e-posta, kredi geçmişi, onaylar, veri indirme, kalıcı silme | KVKK zorunluluğu | Orta | Hayır |
| E10 | **Yazdırılabilir / PDF masal kitabı** | Paylaşım ve hediye; ileride ücretli olabilir | Orta | Hayır |
| E11 | **Çevrimdışı indirme (PWA)** | Zayıf Wi-Fi, yolculuk | Orta | Hayır |
| E12 | **Sakin ses ayarları:** anlatım hızı, ninni/yağmur arka planı, otomatik kapanma | Uyku rutini | Küçük | Hayır |

### Yönetici tarafı

| # | Özellik | Neden | Zorluk | Planda? |
|---|---|---|---|---|
| Y1 | **Kuyruk ve sağlayıcı sağlığı paneli:** bekleyen/başarısız işler, son hatalar, ortalama süreler, Cloudflare günlük kota, TTS servis durumu | Ücretsiz kotalar dar (~90 resim/gün) | Orta | Kısmen (Adım 10) |
| Y2 | **Başarısız işi tek tıkla yeniden dene** (masal, resim, ses ayrı) | Destek yükünü azaltır | Küçük | Hayır |
| Y3 | **Resimli moderasyon kuyruğu:** resim ve sesle inceleme, maskeli ad, karar geçmişi, hazır ret gerekçeleri | Keşfet için zorunlu | Orta | Evet (Adım 9) |
| Y4 | **Kara liste ve prompt şablonu yönetimi** (sürümleme ve önizlemeyle) | Güvenlik ayarı kod dağıtımı istemez | Orta | Evet (Adım 10) |
| Y5 | **Ebeveyn ayrıntı sayfası:** çocuklar (maskeli), masallar, kredi hareketleri, onaylar, oturumları sonlandır | Destek ve hesap verebilirlik | Orta | Kısmen (Adım 10) |
| Y6 | **Ebeveyn şikâyet kutusu:** okuyucuda "Bu masalda uygunsuz bir şey var" düğmesi, yönetimde kuyruk | En güçlü insan güvenlik ağı | Küçük-Orta | Hayır |
| Y7 | **Kalite metrikleri:** tema/anlatım/uzunluk dağılımı, tekrar dinlenme, masal başına süre ve maliyet | Ürün ve maliyet kararları | Orta | Hayır |
| Y8 | **Rol ayrımı ve güvenlik:** moderatör (`ROLE_ADMIN`) seed hesabı, 2FA, parola sıfırlama, son giriş kaydı | En az yetki ilkesi | Küçük-Orta | Hayır |
| Y9 | **Toplu duyuru / e-posta** (SMTP var) | Ebeveynle iletişim | Küçük | Hayır |
| Y10 | **Kütüphane önizlemesi ve sürümleme:** kaydetmeden önce deneme sahne resmi; portreyi gösterme ve yeniden çizme; değişiklik geçmişi ve geri alma | Tek değişiklik bütün ebeveynlerin resimlerini etkiliyor | Orta | Hayır |
| Y11 | **Ebeveyn karakterini kütüphaneye taşıma** ve sezonluk karakter/mekân paketleri (bayram, kış, deniz) | Yeni modelde çeşitlilik yalnızca yönetime bağlı | Küçük-Orta | Hayır |

---

## 3. Önerilen sıra

1. Kayıt formu hatası, çocuk profili metin denetimi, ünlü uyumu hatası (küçük, kullanıcıyı doğrudan etkiliyor).
2. İngilizce kara liste, telefon menüsü kaydırma ipucu, "Son giriş" kaydı, ebeveynler tablosunun kart görünümü.
3. KVKK aydınlatma metni + hesap ve gizlilik merkezi (E9).
4. Moderasyon ekranının resimli hale gelmesi ve ad maskeleme (Y3), kütüphane önizleme ve geçmiş (Y10).
5. Ebeveyn kilidi (E2), uyku modu okuyucu (E1), favoriler ve filtre (E3).
