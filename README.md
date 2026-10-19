# 🌟 AI Destekli Dijital Masal Platformu

3–7 yaş aralığındaki çocuklara yönelik, yapay zekâ teknolojileri kullanılarak kişiselleştirilmiş, eğitici ve görsel açıdan zengin masallar üreten dijital hikâye platformu. 

Bu proje, çocukların yaşına ve pedagojik ihtiyaçlarına uygun temalarla (dostluk, yardımlaşma, doğa sevgisi vb.) güvenli bir ortamda masal okuma ve dinleme deneyimi sunmayı hedefler. Gelişmiş yapay zeka entegrasyonları sayesinde masal metinleri, görseller ve seslendirmeler (TTS) dinamik olarak üretilir.

## 🚀 Öne Çıkan Özellikler

### Temel Özellikler 
- **👥 Çoklu Profil Yönetimi:** Ebeveyn hesabı altında birden fazla çocuk profili oluşturma.
- **✨ Kişiselleştirilmiş Masal Üretimi:** Ana/yan karakter, ortam, tema ve anlatım biçimi seçimi.
- **🎨 Otomatik Görselleştirme:** Üretilen masalın anlamlı sahnelere (4-6 sahne) ayrılması ve her sahneye özel görsel üretimi.
- **🎧 Sesli Anlatım (TTS):** Hikaye metinlerinin metinden sese teknolojisi ile seslendirilmesi.
- **📚 Kütüphane ve Keşfet Alanı:** Masalları özel (private) veya herkese açık (public) kaydetme. Herkese açık masalların onay sonrası "Keşfet" ekranında yayınlanması.
- **🛡️ Yönetim Paneli:** Adminler için kullanıcı ve masal içerik denetimi (Moderasyon).

### Gelişmiş Özellikler 
- **🤖 Masal Sonrası Sohbet Asistanı:** Çocuğun okuduğu masal üzerine düşünmesini destekleyen, pedagojik sorular soran etkileşimli yapay zeka asistanı.
- **🛤️ Dallanan Hikaye Yapısı (Karar Anları):** Masalın belirli noktalarında çocuğa seçenekler sunarak hikayenin gidişatını belirleme imkanı.

## 🛠️ Kullanılan Teknolojiler

- **Backend:** Java, Spring Boot, Spring Security, Spring Data JPA
- **Frontend (Web):** Thymeleaf, HTML5, CSS3, JavaScript
- **API (Mobil İçin):** RESTful Architecture, JWT Authentication
- **Veritabanı:** PostgreSQL
- **Yapay Zeka (LLM):** Gemini API (Esnek/Değiştirilebilir Mimari)
- **Medya API'leri:** Stable Diffusion / DALL-E (Görsel Üretimi), Google TTS (Seslendirme)

## 🗄️ Veritabanı Mimarisi (PostgreSQL)

Proje, genişletilebilir bir ilişkisel veritabanı şeması üzerine inşa edilmiştir:
- `users`: Ebeveyn ve Admin hesapları.
- `child_profiles`: Ebeveyne bağlı çocuk profilleri.
- `stories` & `story_scenes`: Masal üst verisi ve masalı oluşturan paragraflar/resimler.
- `story_choices`: Etkileşimli masallar için karar ağaçları.
- `favorites`: Kullanıcıların favorilediği masallar.
- `chat_sessions` & `chat_messages`: Çocuk ve AI asistan arasındaki sohbet geçmişi.

## 📂 Proje Klasör Yapısı

Platform, web (Thymeleaf) ve mobil (REST API) istemcileri aynı anda destekleyecek katmanlı (Layered) bir mimaride tasarlanmıştır. AI servisleri bağımlılığı azaltmak için arayüzler (Interface) üzerinden haberleşir.

```text
com.project.masal_atolyesi
├── config/                 # Async, RestTemplate, Security ve WebMVC ayarları
├── controller/             # İstek karşılayıcılar
│   ├── web/                # Thymeleaf HTML Controller'ları (WebUI)
│   └── api/                # Mobil cihazlar için JSON/JWT tabanlı REST API'ler
├── dto/                    # Veri transfer objeleri (Request/Response)
├── entity/                 # Veritabanı tabloları (JPA Entities)
├── exception/              # Global hata yönetimi
├── repository/             # Veritabanı sorguları (Spring Data JPA)
├── security/               # JWT ve Web Security filtreleri
├── service/                # İş kuralları (Business Logic)
│   ├── ai/                 # LLM, Görsel ve TTS servis arayüzleri
│   ├── core/               # Ana sistem servis arayüzleri
│   └── impl/               # Servis implementasyonları (Gemini, GoogleTTS vb.)
└── util/                   # Prompt formatlama, dosya kaydetme ve metin bölme araçları
