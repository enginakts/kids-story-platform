# 🌟 Masal Dünyası AI - 3-6 Yaş Yapay Zekâ Masal Platformu

3–6 yaş aralığındaki çocuklara yönelik; ebeveyn denetimli, görsel ve karakter tutarlılığı (Bibles) korunan, pedagojik güvenlik filtreli, tek seferlik ve dallanan (karar ağaçlı) masallar üreten yeni nesil yapay zekâ hikâye platformu.

---

## 🛠️ Teknoloji Yığını

* **Backend:** Java 17, Spring Boot 3.4.1, Spring Data JPA, Spring Security, JJWT (0.12.6), SQLite (Geliştirme & Test) / PostgreSQL (Üretim).
* **Frontend:** React 19, Vite, Modern Vanilla CSS Design System, Responsive Glassmorphism UI.
* **Veritabanı Şeması:** 20 Tabloluk optimize Faz 1 MVP mimarisi (`admins` ve `parents` ayrımı, `role_id` optimizasyonu, Karakter ve Mekân İncilleri).

---

## 📂 Proje Yapısı

```text
kids-histories/
├── backend/                                   # Spring Boot (Java 17) REST API
│   ├── src/main/java/com/kidstory/backend/
│   │   ├── config/                            # Security & CORS yapılandırması
│   │   ├── controller/                        # REST Controller'lar (AuthController vb.)
│   │   ├── dto/                               # Request/Response DTO sınıfları
│   │   ├── entity/                            # 20 Tablonun JPA Entity modelleri
│   │   ├── repository/                        # Spring Data JPA Repository arayüzleri
│   │   ├── security/                          # JWT Token Provider & Filter
│   │   └── service/                           # İş mantığı servisleri (AuthService vb.)
│   ├── src/main/resources/
│   │   └── application.properties             # SQLite & JPA ddl-auto=update ayarları
│   └── pom.xml                                # Maven bağımlılıkları
│
├── frontend/                                  # Vite + React 19 Arayüzü
│   ├── src/
│   │   ├── components/
│   │   │   ├── ParentAuth.jsx                 # Ebeveyn Giriş & Kayıt Portalı
│   │   │   ├── AdminAuth.jsx                  # Yönetici Giriş Konsolu (Obsidiyen Kalkan Teması)
│   │   │   └── UserDashboard.jsx              # Giriş sonrası oturum ve kredi göstergesi
│   │   ├── App.jsx                            # Portal geçişi ve ana navigasyon
│   │   ├── App.css                            # Arayüz ve tema stilleri
│   │   └── index.css                          # Global tasarım değişkenleri
│   └── index.html
│
├── platform_mimari_ve_veritabani_rehberi.md   # 20 Tabloluk detaylı şema ve DDL rehberi
├── adim_adim_gelistirme_plani.md              # Parça parça ilerleyen mikro geliştirme yol haritası
├── veritabani_sema_semasi.png                 # Yüksek çözünürlüklü Türkçe ERD diyagramı
└── veritabani_sema_semasi.html                # Dinamik şema inceleme tarayıcısı
```

---

## 🚀 Başlangıç ve Çalıştırma

### 1. Backend (Spring Boot)
```bash
cd backend
./mvnw spring-boot:run
```
* Backend API: `http://localhost:8080`
* SQLite Veritabanı: `backend/kidstory.db`

### 2. Frontend (React + Vite)
```bash
cd frontend
npm run dev
```
* Frontend Web: `http://localhost:5173`
  * Ebeveyn Portalı: `http://localhost:5173/#parent`
  * Yönetici Portalı: `http://localhost:5173/#admin`

---

## 📌 Geliştirme Yol Haritası
Proje, adım adım küçük parçalar halinde geliştirilmektedir. Detaylı geliştirme planını [adim_adim_gelistirme_plani.md](./adim_adim_gelistirme_plani.md) dosyasından takip edebilirsiniz.
