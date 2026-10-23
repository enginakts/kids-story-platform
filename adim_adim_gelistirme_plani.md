# 3-6 Yaş Yapay Zekâ Masal Platformu - Adım Adım Geliştirme Yol Haritası

Bu doküman; projenin **"küçük, test edilebilir, parça parça ilerleyen"** geliştirme metodolojisini ve bir sonraki etaplarda sırasıyla yapılacak mikro adımları içerir.

---

## 🎯 Temel Çalışma Prensibimiz (Workflow)

1. **Küçük ve Odaklı Adımlar:** Her aşamada yalnızca tek bir mikro modül ele alınır.
2. **.NET Core Referansı:** Her adımda talep edilen yapının .NET Core karşılığı (`ControllerBase`, `DbContext`, `Service`, `Middleware`, `DTO`) açıklanır ve ardından Spring Boot karşılığı kodlanır.
3. **Tamamlayıcı Çift Yönlü Geliştirme:** Her adım hem **Backend (Controller, Service, Repository, DTO)** hem de **Frontend (React UI, kullanıcı deneyimi, test butonları)** ile birlikte çalışır vaziyette sunulur.
4. **İnceleme & Onay Döngüsü:** Bir adım bitirilip test edilmeden ve kullanıcıdan onay alınmadan kesinlikle bir sonraki adıma geçilmez.

---

## 📊 Genel İlerleme Tablosu

| Adım No | Modül / Özellik | Backend Durumu | Frontend Durumu | Durum |
| :---: | :--- | :---: | :---: | :---: |
| **Adım 1** | Kimlik Doğrulama & Ebeveyn/Yönetici Ayrımı | Tamamlandı | Tamamlandı |  **ONAYLANDI** |
| **Adım 2** | Çocuk Profili Yönetimi, Navbar, Footer & İzolasyon | Tamamlandı | Tamamlandı |  **İNCELEMEDE** |
| **Adım 3** | Karakter ve Mekân Tutarlılık Rehberi (Bibles) | Beklemede | Beklemede | ⏳ *Sıradaki Adım* |
| **Adım 4** | Masal Sihirbazı & Tek Seferlik Masal Üretimi | Beklemede | Beklemede | 📋 Planlandı |
| **Adım 5** | Masal Okuyucu & Sahne Sahne Oynatıcı | Beklemede | Beklemede | 📋 Planlandı |
| **Adım 6** | Etkileşimli Masal Motoru (A/B Karar Ağacı) | Beklemede | Beklemede | 📋 Planlandı |
| **Adım 7** | Ebeveyn Güvenlik Kilidi (Parental Gate) | Beklemede | Beklemede | 📋 Planlandı |
| **Adım 8** | Kredi Cüzdanı & Abonelik Paket Yönetimi | Beklemede | Beklemede | 📋 Planlandı |
| **Adım 9** | Pedagojik Güvenlik & Kara Liste Filtresi | Beklemede | Beklemede | 📋 Planlandı |
| **Adım 10**| Yönetici Denetim Konsolu & AI Yapılandırması | Beklemede | Beklemede | 📋 Planlandı |

---

## 🛠️ Sıradaki Adımların Detaylı Kapsamı

---

### 👶 ADIM 2: Çocuk Profili Yönetimi ve Veri İzolasyonu (SIRADAKİ ADIM)
> **Amaç:** Ebeveynin çocuklarını tanımlaması, çocukların ilgi alanlarını (hayvanlar, renkler, uyku saati) belirlemesi ve masalların sadece ilgili çocuğa özel kalması (veri izolasyonu).

* **.NET Core Karşılığı:**
  * `[Authorize]` korumalı `ChildrenController : ControllerBase`
  * `IChildService` ve `ChildService`
  * `CreateChildDto`, `UpdateChildDto`, `ChildResponseDto`
  * `User.FindFirst(ClaimTypes.NameIdentifier)` ile ebeveyn ID'sini token'dan alma.
* **Backend (Spring Boot):**
  * `ChildProfileController`:
    * `GET /api/children` -> Giriş yapan ebeveynin çocuklarını listeler.
    * `POST /api/children` -> Yeni çocuk profili ekler (Ad, yaş: 2-8, cinsiyet, sevdiği hayvanlar, renkler, özel ilgi alanları, uyku saati).
    * `PUT /api/children/{id}` -> Çocuk profilini günceller.
    * `DELETE /api/children/{id}` -> Çocuk profilini pasife alır/siler.
  * `ChildProfileService` & `ChildProfileRepository`.
* **Frontend (React):**
  * Ebeveyn paneline "Çocuklarım" yönetim kartı.
  * Sevimli çocuk avatarı seçici, yaş kaydırıcısı (2-8 yaş), renkli etiketler (kediler, dinozorlar, uzay).
  * Aktif çocuk profili seçici (Hangi çocuk için masal üretilecek?).
* **Test Senaryosu:** Mehmet Kaya hesabıyla giriş yapıp Zeynep (4) ve Ali (6) profillerini görüntüleme, yeni bir çocuk ekleme ve güncelleme.

---

### 🎨 ADIM 3: Karakter ve Mekân Tutarlılık Rehberi (Character & Location Bibles)
> **Amaç:** Masallardaki kahramanın ve ortamın sahneler boyunca görsel olarak aynı kalmasını sağlayan şablon yapısı.

* **.NET Core Karşılığı:**
  * `BiblesController : ControllerBase`
  * Sistem şablonları için `SeedData` ve özel kullanıcı şablonları servisi.
* **Backend (Spring Boot):**
  * `CharacterBibleController` & `LocationBibleController`:
    * `GET /api/bibles/characters` -> Sistem hazır karakterleri (Örn: Sevimli Tavşan Pamuk, Yavru Aslan Leo) ve çocuğa özel karakterler.
    * `POST /api/bibles/characters` -> Çocuğa özel yeni karakter tanımlama (görsel çapa tokenleri, kişilik, ses kimliği).
    * `GET /api/bibles/locations` -> Hazır mekânlar (Büyülü Orman, Bulut Şatosu, Dinozor Adası).
* **Frontend (React):**
  * Karakter kartları galerisi (Görsel önizleme, tür, belirgin özellikler).
  * Mekân kartları galerisi (Işık stili, atmosfer tanımı).
  * Çocuk için kahraman ve mekân seçici bileşeni.
* **Test Senaryosu:** Hazır sistem karakterlerini listeleme ve Zeynep için özel "Gözlüklü Akıllı Kedi" karakteri tanımlama.

---

### 📖 ADIM 4: Masal Üretim Sihirbazı (Faz 1: Tek Seferlik Masal - Single Shot)
> **Amaç:** Ebeveynin seçtiği çocuk, karakter, mekân ve temaya göre 4 sahnelik pedagojik bir masal oluşturulması.

* **.NET Core Karşılığı:**
  * `StoryGenerationService` (Background Worker / Task tabanlı üretim kuyruğu mantığı).
  * `GenerateStoryDto`, `StoryDetailDto`.
* **Backend (Spring Boot):**
  * `StoryController`:
    * `POST /api/stories/generate-single` -> Masal üretim isteği alır, ebeveyn cüzdanından 1 masal kredisi düşer.
    * `GET /api/stories/child/{childId}` -> Çocuğun masal kitaplığı.
    * `GET /api/stories/{id}` -> Masalın tüm sahneleri, metinleri, görsel ve ses URL'leri.
  * Masal durum takibi (`PENDING` -> `GENERATING` -> `COMPLETED`).
* **Frontend (React):**
  * 3 Adımlı Sihirbaz Formu:
    1. Masal Teması Seçimi (Uyku masalı, cesaret, paylaşmak, doğa sevgisi).
    2. Çocuk ve Kahraman Seçimi.
    3. Üretim Durum Animasyonu (Yapay zekâ masalınızı yazıyor...).
* **Test Senaryosu:** Zeynep için "Yıldızları Arayan Sevimli Kedi" temalı 4 sahnelik masal oluşturulması ve cüzdandaki kredinin 15'ten 14'e düştüğünün doğrulanması.

---

### 🎧 ADIM 5: Masal Okuyucu & Sahne Sahne Oynatıcı (Story Reader UI)
> **Amaç:** Üretilen masalın 3-6 yaş çocuğun rahatça takip edebileceği, sayfa çevirme hissi veren sesli ve görsel oynatıcısı.

* **Backend (Spring Boot):**
  * `ChildReadingSessionController`:
    * `POST /api/reading-sessions/start` -> Dinleme seansı başlatır.
    * `POST /api/reading-sessions/complete` -> Dinleme süresini ve tamamlanma durumunu kaydeder.
* **Frontend (React):**
  * Çocuk dostu, dikkat dağıtmayan tam ekran okuma modu.
  * Büyük harfli pedagojik tipografi.
  * Sahne görseli alanı (WebP optimizasyonlu).
  * Ses oynatma çubuğu (Play/Pause, hız ayarı, tekrar dinle).
  * "Sonraki Sahne" ve "Önceki Sahne" butonları.
* **Test Senaryosu:** Oluşturulan masalın 1'den 4. sahneye kadar ses ve görselle birlikte akıcı şekilde dinlenmesi ve seans kaydının tutulması.

---

### 🎮 ADIM 6: Etkileşimli Masal Motoru (A/B Karar Ağacı - Branching Nodes)
> **Amaç:** Masalın ortasında çocuğa 2 seçenek sunularak akışı çocuğun belirlemesi (Örn: "Tavşan sol yoldaki mağaraya mı gitsin, sağdaki çiçekli köprüye mi?").

* **.NET Core Karşılığı:**
  * Karar ağacı durum makinesi (State Machine), `MakeChoiceDto`.
* **Backend (Spring Boot):**
  * `InteractiveStoryController`:
    * `POST /api/stories/interactive/start` -> 1 etkileşimli kredi harcayarak 1. sahneyi ve karar düğümünü döner.
    * `POST /api/stories/interactive/choose` -> Çocuğun A veya B seçimini alır (`selected_choice`), bir sonraki sahneyi üretir.
* **Frontend (React):**
  * 3-6 yaş çocukların okuma bilmeden dokunabileceği devasa iki seçenek kartı (Görsel ikonlu ve canlı renkli Seçenek A & Seçenek B).
* **Test Senaryosu:** Etkileşimli masal başlatılması, Seçenek A seçilerek masalın o dalda devam etmesi ve tamamlanması.

---

### 🔒 ADIM 7: Ebeveyn Güvenlik Kilidi (Parental Gate)
> **Amaç:** Çocuğun uygulama içinde gezinirken yanlışlıkla kredi harcamasını, ayarları değiştirmesini veya dış bağlantılara tıklamasını önlemek.

* **.NET Core Karşılığı:**
  * `ParentalGateMiddleware` veya Controller filtre doğrulaması.
* **Backend (Spring Boot):**
  * `ParentalGateController`:
    * `GET /api/parental-gate/challenge` -> Rastgele pedagojik ebeveyn sorusu üretir (Örn: "Dört kere sekiz kaç eder?", "Ekranda yazan sayıyı tersinden girin").
    * `POST /api/parental-gate/verify` -> Cevabı kontrol eder, doğrulanırsa geçici işlem izni verir; denemeyi `parental_gate_attempts` tablosuna loglar.
* **Frontend (React):**
  * Ebeveyn menüsüne veya kredi satın almaya tıklandığında açılan şık güvenlik modalı.
* **Test Senaryosu:** Kredi veya ayarlar butonuna basıldığında sorunun gelmesi, yanlış cevapta engellenmesi, doğru cevapta işlemin açılması.

---

### 💳 ADIM 8: Kredi Cüzdanı & Abonelik Yönetimi
> **Amaç:** Ebeveynin kalan kredilerini görmesi, paket detaylarını incelemesi ve kredi yükleme işlemlerinin takibi.

* **Backend (Spring Boot):**
  * `WalletController`: Kredi bakiyesi ve `credit_transactions` hareket dökümü.
  * `SubscriptionPlanController`: 3 paketin (`FREE_TIER`, `STANDARD_MONTHLY`, `FAMILY_VIP`) listelenmesi ve abonelik durumu.
* **Frontend (React):**
  * Kredi cüzdanı detay kartı (Kullanılan, kalan masallar).
  * Paket yükseltme vitrini (Fiyatlar, aylık masal limitleri, saklama süreleri).
* **Test Senaryosu:** Mehmet Kaya'nın 15 kredisinin dökümünü görmesi, paketler arasında karşılaştırma yapabilmesi.

---

### 🛡️ ADIM 9: Pedagojik Güvenlik & Kara Liste Filtresi
> **Amaç:** Masallarda korku, şiddet, ayrılık anksiyetesi, kabus gibi 3-6 yaşa zararlı kelimelerin kesinlikle yer almaması.

* **.NET Core Karşılığı:**
  * Request/Response Pipeline Action Filter (`ContentSanitizerFilter`).
* **Backend (Spring Boot):**
  * `ContentFilterService`: `content_blacklists` tablosundaki kelimeleri tarar; prompt'a negatif kurallar ekler ve gelen masal metnini denetler.
  * Zararlı kelime tespit edilirse otomatik güvenli ikame kelimeyle değiştirir veya üretimi engeller.
* **Frontend (React):**
  * Ebeveyne masalın pedagojik güvenlik onay rozetini gösterme ("Pedagojik Olarak Onaylandı ✅").
* **Test Senaryosu:** Kara listede olan "karanlık cadı" veya "korkunç canavar" kelimeleri girildiğinde filtrenin devreye girerek metni çocuk dostu hale getirmesi.

---

### 👑 ADIM 10: Yönetici Denetim Konsolu & AI Ayarları
> **Amaç:** Yöneticilerin sistem ayarlarını, prompt şablonlarını, AI modellerini ve denetim loglarını yönetmesi.

* **Backend (Spring Boot):**
  * `AdminDashboardController` (`ROLE_ADMIN` & `ROLE_SUPER_ADMIN` yetkili):
    * Kara liste yönetimi (yeni yasaklı kelime ekleme/çıkarma).
    * Prompt şablonları düzenleme (`prompt_templates`).
    * AI sağlayıcı öncelik sıralaması (`ai_providers_config`).
    * Yönetici işlem kayıtları (`admin_audit_logs`).
* **Frontend (React - Yönetici Portalı):**
  * Modern koyu tema yönetim masası (Sistem metrikleri, yasaklı kelime tablosu, prompt düzenleyici).
* **Test Senaryosu:** `superadmin` girişiyle yeni bir kara liste kelimesi eklenmesi ve denetim logunun oluşması.

---

## 📌 Sonraki Eylem
Kullanıcı incelemesini tamamladıktan sonra, hazır olduğunda **ADIM 2 (Çocuk Profili Yönetimi ve Veri İzolasyonu)** ile başlanacaktır.
