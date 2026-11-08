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
| Masal metni | Trendyol LLM (`Trendyol-LLM-8B-T1`), Featherless AI | `AiRouter`, `ai_providers_config` (`LLM`) öncelik sırasıyla sağlayıcı dener |
| Sahnelere bölme | Google Gemini Flash (`gemini-3.6-flash`) | Aynı yönlendirici, `SCENE` rolü; JSON şemalı çıktı |
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

`exception/` ve `service/ai/` eski yapıya eklenen iki yeni klasördür. Arayüz kuralları [tasarim_sistemi.md](./tasarim_sistemi.md) dosyasındadır. Web arayüzü Thymeleaf ile sunucuda üretildiği için ayrı bir React `frontend/` uygulaması yoktur; sayfalar `backend/src/main/resources/templates` altındadır.

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
| `http://localhost:8080/admin` | Yönetim paneli: genel bakış, masallar ve içerik onayı, ebeveynler (kredi ekleme, askıya alma). Süper yönetici ayrıca yapay zekâ sağlayıcılarını ve yönetici hesaplarını yönetir. Her değişiklik `admin_audit_logs` tablosuna yazılır |

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

Masal üretimi iki aşamalıdır: metni **Trendyol LLM** (Featherless AI) yazar, **Gemini Flash** sahnelere böler. Üretimde `FEATHERLESS_API_KEY` ve `GEMINI_API_KEY` zorunludur. Geliştirmede anahtarı olmayan aşama için sabit bir taslak kullanılır. Yerelde gerçek sağlayıcılarla denemek için `backend/.env.example` dosyasını `backend/.env` adıyla kopyalayıp anahtarları yazın (`GROQ_API_KEY=...` biçiminde). `.env` git'e girmez ve uygulama `backend` klasöründen çalıştırıldığında kendiliğinden okunur. Aynı adlı ortam değişkenleri `.env`'deki değerleri ezer.

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
| GET, POST | `/api/v1/characters` | Ebeveynin karakterleri ve hazır karakterler; yeni karakter |
| PUT, DELETE | `/api/v1/characters/{id}` | Karakteri günceller, pasife alır |
| GET | `/api/v1/characters/options` | Tür, renk, kıyafet ve aksesuar seçenekleri |
| GET | `/api/v1/locations` | Sistem mekân şablonları |
| POST | `/api/v1/stories` | Masal ister (1 kredi); 202 döner, masal arka planda hazırlanır |
| GET | `/api/v1/stories` | Ebeveynin masalları ve durumları |
| GET | `/api/v1/stories/{id}` | Masalın sahneleri |
| GET | `/api/v1/stories/options` | Tema, anlatım biçimi ve uzunluk seçenekleri |

Ebeveyn kimliği her zaman token'dan okunur, istekten alınmaz. Erişim token'ı 15 dakika, yenileme token'ı 30 gün geçerlidir.

Giriş güvenliği: aynı hesaba 5 hatalı denemede hesap 15 dakika kilitlenir. Giriş, kayıt ve parola uçlarına IP başına dakikada 20 istek sınırı uygulanır. Bu sayaçlar bellektedir; birden fazla sunucuya geçildiğinde paylaşımlı depoya taşınmalıdır.

## Yol haritası

Durum ve sıradaki adımlar için [adim_adim_gelistirme_plani.md](./adim_adim_gelistirme_plani.md) dosyasına bakın.
