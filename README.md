# Masal Dünyası AI

3-7 yaş çocuklar için yapay zekâ ile kişiselleştirilmiş, resimli ve sesli masallar üreten, ebeveyn denetimli Türkçe platform.

Proje tek bir Spring Boot uygulamasıdır. Web arayüzü Thymeleaf ile sunucuda üretilir, mobil uygulama aynı iş mantığını REST API üzerinden kullanır.

## Mimari kararlar

| Konu | Karar | Uygulaması |
| :--- | :--- | :--- |
| Arka uç | Spring Boot 4.1, Java 21 | Tek uygulama, özelliğe göre paketler |
| Web | Thymeleaf | `/`, `/app/**`, `/admin/**`; oturum ve CSRF korumalı |
| Mobil | REST API | `/api/v1/**`; JWT, oturumsuz. Mobil istemci sonraki aşamada |
| Veritabanı | PostgreSQL 16 | Şema yalnızca Flyway ile yönetilir |
| LLM | Hibrit, değiştirilebilir | `AiRouter`, `ai_providers_config` öncelik sırasıyla sağlayıcı dener |
| Görsel ve TTS | Dış API | `ImageClient`, `TtsClient` arayüzleri; şu an yalnızca taslak adaptörler var |
| Üretim kuyruğu | Veritabanı kuyruğu | `generation_jobs`; ücretsiz katmanda Redis gerektirmez |
| Giriş | E-posta ve parola | Ebeveyn ve yönetici ayrı tablo, ayrı giriş |
| Dağıtım | Önce yerel, sonra bulut | `dev` ve `prod` profilleri |

## Proje yapısı

```text
kids-story-platform/
├── backend/                                   # Spring Boot (Java 21)
│   ├── src/main/java/com/kidstory/backend/
│   │   ├── config/                            # Güvenlik zincirleri, ilk yönetici kurulumu
│   │   ├── controller/                        # REST API (AuthController vb.) ve Thymeleaf sayfa controller'ları
│   │   ├── dto/                               # Request/Response sınıfları
│   │   ├── entity/                            # 29 tablonun JPA entity modelleri
│   │   ├── exception/                         # Hata sınıfları ve API hata yönetimi
│   │   ├── repository/                        # Spring Data JPA repository arayüzleri
│   │   ├── security/                          # JWT üretimi/doğrulaması, kullanıcı yükleme
│   │   └── service/                           # İş mantığı (AuthService vb.)
│   │       └── ai/                            # Değiştirilebilir LLM, görsel ve TTS katmanı
│   ├── src/main/resources/
│   │   ├── db/migration/                      # Flyway şeması (V1 çekirdek, V2 başlangıç verisi, V3 sohbet)
│   │   ├── templates/                         # Thymeleaf sayfaları
│   │   ├── static/css/                        # Arayüz stilleri
│   │   └── application*.properties            # dev / prod profilleri
│   └── pom.xml
├── docker-compose.yml                         # İsteğe bağlı yerel PostgreSQL
├── platform_mimari_ve_veritabani_rehberi.md   # Tablo ve ilişki rehberi
├── kullanilan_modeller_rehberi.md             # Yapay zekâ sağlayıcı adayları
└── adim_adim_gelistirme_plani.md              # Yol haritası
```

`exception/` ve `service/ai/` eski yapıya eklenen iki yeni klasördür. Web arayüzü Thymeleaf ile sunucuda üretildiği için ayrı bir React `frontend/` uygulaması yoktur; sayfalar `backend/src/main/resources/templates` altındadır.

Şema `backend/src/main/resources/db/migration` altındadır ve 29 tablodan oluşur. Tablo açıklamaları için [platform_mimari_ve_veritabani_rehberi.md](./platform_mimari_ve_veritabani_rehberi.md) dosyasına bakın.

## Çalıştırma

Gereksinim: Java 21. Docker zorunlu değildir.

### Docker olmadan (gömülü PostgreSQL)

```bash
cd backend
./mvnw spring-boot:test-run -Dspring-boot.run.main-class=com.kidstory.backend.LocalDevApplication
```

Veriler `backend/.devdb` klasöründe kalıcıdır. Sıfırlamak için uygulamayı kapatıp bu klasörü silin.

### Docker ile

```bash
docker compose up -d
cd backend
./mvnw spring-boot:run
```

### Adresler

| Adres | Açıklama |
| :--- | :--- |
| `http://localhost:8080/` | Ana sayfa |
| `http://localhost:8080/register` | Ebeveyn kaydı |
| `http://localhost:8080/admin/login` | Yönetici girişi (`dev`: `superadmin` / `DevAdmin123!`) |

Bu yönetici hesabı yalnızca `dev` profiline aittir.

### Testler

```bash
cd backend
./mvnw test
```

Testler Docker olmadan gerçek PostgreSQL üzerinde çalışır.

## Üretim ortamı

`SPRING_PROFILES_ACTIVE=prod` ile çalıştırılır. Şu değişkenler zorunludur; eksikse uygulama başlamaz:

* Veritabanı: `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
* Güvenlik: `JWT_SECRET` (en az 32 bayt)
* Bağlantılar: `APP_BASE_URL` (örn. `https://masaldunyasi.com`)
* E-posta (herhangi bir SMTP sağlayıcısı): `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`; isteğe bağlı `MAIL_PORT` (587)

Geliştirmede e-posta gönderilmez; doğrulama ve sıfırlama bağlantıları konsol log'una yazılır.

İlk yönetici için `ADMIN_USERNAME`, `ADMIN_EMAIL` ve `ADMIN_PASSWORD` verilir. Taslak yapay zekâ adaptörleri `prod` profilinde yüklenmez.

## REST API

| Yöntem | Yol | Açıklama |
| :--- | :--- | :--- |
| POST | `/api/v1/auth/register` | Ebeveyn kaydı; erişim ve yenileme token'ı döner |
| POST | `/api/v1/auth/login` | Giriş; erişim ve yenileme token'ı döner |
| POST | `/api/v1/auth/refresh` | Yenileme token'ını yenisiyle değiştirir |
| POST | `/api/v1/auth/logout` | Yenileme token'ını iptal eder |
| POST | `/api/v1/auth/verify-email` | E-posta doğrulama |
| POST | `/api/v1/auth/password/forgot` | Parola sıfırlama bağlantısı ister |
| POST | `/api/v1/auth/password/reset` | Yeni parolayı kaydeder |
| POST | `/api/v1/account/verify-email/resend` | Doğrulama bağlantısını yeniden gönderir |
| GET, POST | `/api/v1/children` | Çocukları listeler, ekler |
| PUT, DELETE | `/api/v1/children/{id}` | Çocuğu günceller, pasife alır |
| GET | `/api/v1/children/{id}/story-options` | Sihirbaz seçenekleri: karakter, mekân, değer, durum, duygu, anlatım |
| GET | `/api/v1/children/{id}/stories` | Yalnızca o çocuğun masalları |
| POST | `/api/v1/stories` | Masal üretimini başlatır (1 kredi); 202 döner, üretim asenkrondur |
| GET | `/api/v1/stories/{id}` | Durum (`PENDING`, `GENERATING`, `COMPLETED`, `FAILED`), ilerleme ve sahneler |
| GET | `/api/v1/stories/{id}/scenes/{n}/image`, `/audio` | Sahne görseli ve sesi (yalnızca masalın sahibi) |

### Masal üretim hattı

1. Ebeveyn sihirbazda çocuğu, ana ve yan karakterleri, mekânı, öğretilecek değeri, durumu, duyguyu, anlatımı ve sahne sayısını (4-6) işaretler. Serbest metin yoktur.
2. Bir kredi düşer, `generation_jobs` kuyruğuna `STORY_TEXT` işi eklenir. Çocuğun adı yapay zekâya gönderilmez.
3. LLM masalı JSON olarak yazar; yanıt şemaya göre doğrulanır ve `content_blacklists` kurallarından geçer. Masal sahnelere bölünür.
4. Her sahne için `SCENE_IMAGE` ve `SCENE_AUDIO` işleri eklenir. Görsel prompt'u her sahnede aynı stil sabiti, karakter ve mekân rehberinin (bible) görsel tanımları ve karakterin sabit seed'i ile kurulur; LLM yalnızca sahnedeki eylemi yazar.
5. Hata alan iş artan beklemeyle 3 kez denenir. Denemeler biterse masal `FAILED` olur ve kredi iade edilir.

Gerçek sağlayıcı eklemek için `LlmClient`, `ImageClient` veya `TtsClient` uygulayan bir adaptör ve `ai_providers_config` tablosunda bir satır yeterlidir. Dosyalar `app.storage.dir` (`STORAGE_DIR`) altında tutulur.

Ebeveyn kimliği her zaman token'dan okunur, istekten alınmaz. Erişim token'ı 15 dakika, yenileme token'ı 30 gün geçerlidir.

Giriş güvenliği: aynı hesaba 5 hatalı denemede hesap 15 dakika kilitlenir. Giriş, kayıt ve parola uçlarına IP başına dakikada 20 istek sınırı uygulanır. Bu sayaçlar bellektedir; birden fazla sunucuya geçildiğinde paylaşımlı depoya taşınmalıdır.

## Yol haritası

Durum ve sıradaki adımlar için [adim_adim_gelistirme_plani.md](./adim_adim_gelistirme_plani.md) dosyasına bakın.
