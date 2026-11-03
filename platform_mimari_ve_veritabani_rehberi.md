# 3-7 Yaş Yapay Zekâ Masal Platformu - Mimari, Veritabanı ve İlişki Şeması (Faz 1 MVP)

Bu doküman; 3-7 yaş çocuklar için ebeveyn denetimli, tek seferlik ve etkileşimli masal platformunun **1. Etabı (MVP)** için optimize edilmiş 20 tabloluk veritabanı şemasını, sütun detaylarını, ilişkilerini ve PostgreSQL DDL kodlarını içerir.


---

## 1. Faz 1 Optimizasyon ve Sadeleştirme Kararları

1. **`users` ve `user_profiles` Ayrıştırıldı:**
   * İdari personel için **`admins`**, masal üreten ve çocuklarını yöneten ebeveynler için **`parents`** tablosu oluşturuldu.
   * `user_profiles` kaldırıldı, tüm ebeveyn alanları doğrudan `parents` içine alındı (veri tabanı şişmesi ve gereksiz JOIN önlendi).
2. **Rol Yapısı Sadeleştirildi (`role_id`):**
   * Faz 1 için gereksiz olan `user_roles`, `permissions` ve `role_permissions` ara tabloları elendi.
   * Hem `admins` hem de `parents` tablolarına doğrudan **`role_id`** sütunu eklendi (`roles` tablosuna FK).
3. **İlk Etap İçin Elenen Tablolar:**
   * `child_badges` ve `child_achievements` (Oyunlaştırma Faz 2'ye ertelendi).
   * `broadcast_announcements` (Toplu duyurular Faz 2'ye ertelendi).
   * `invoices` (Fatura ve bakiye takibi `credit_transactions` üzerinden yürütülecektir).
   * `user_sessions` (Stateless JWT token mimarisine geçilerek DB oturum tablosu yükü kaldırıldı).
   * `story_characters` ilk etapta elenmişti; yan karakter gereksinimi nedeniyle geri eklendi (bkz. Bölüm 4).
4. **Karakter ve Mekân Tutarlılığı (Bibles) Korundu:**
   * Görsel kaymasını önleyen `character_bibles` ve `location_bibles` korundu.
   * Çocukların birbirinin masalını görmesini engelleyen `child_profiles` veri izolasyonu korundu.

---

## 2. Tablolar ve Sütun Detayları (Türkçe Açıklamalı)

### 👤 Modül 1: Kimlik Doğrulama & Yönetim
* **`roller (roles)`:** Sistemdeki rol tanımları (`ROLE_PARENT`, `ROLE_ADMIN`, `ROLE_SUPER_ADMIN`).
  * `id` [PK], `rol_kodu (name)` [UQ], `gorunen_ad (display_name)`, `aciklama (description)`, `olusturuldu_tarih (created_at)`
* **`yoneticiler (admins)`:** İdari personel ve moderatör hesapları.
  * `id` [PK], `kullanici_adi (username)` [UQ], `eposta (email)` [UQ], `sifre_hash (password_hash)`, `ad_soyad (full_name)`, `rol_id` [FK -> roller], `aktif_mi (is_active)`, `son_giris_tarih (last_login_at)`
* **`ebeveynler (parents)`:** Masal üreten ve çocuklarını kaydeden ebeveynler.
  * `id` [PK], `eposta (email)` [UQ], `sifre_hash (password_hash)`, `ad (first_name)`, `soyad (last_name)`, `telefon (phone_number)`, `rol_id` [FK -> roller], `tercih_edilen_dil (preferred_language)`, `avatar_url`, `aktif_mi (is_active)`, `eposta_onayli_mi (is_email_verified)`

### 👶 Modül 2: Çocuk Profili & İzolasyon
* **`cocuk_profilleri (child_profiles)`:** Her çocuğun izole masal kitaplığı ve kişiselleştirme tercihleri.
  * `id` [PK], `ebeveyn_id (parent_id)` [FK -> ebeveynler], `ad (name)`, `yas (age: 3-7)`, `cinsiyet (gender)`, `sevdigi_hayvanlar`, `sevdigi_renkler`, `ozel_ilgi_alanlari`, `uyku_saati (bedtime_hour)`, `avatar_url`, `aktif_mi`
* **`okuma_seanslari (child_reading_sessions)`:** Çocuğun masalı dinleme süresi ve tamamlama geçmişi.
  * `id` [PK], `cocuk_id (child_id)` [FK -> cocuk_profilleri], `masal_id (story_id)` [FK -> masallar], `dinleme_suresi_sn (duration_seconds)`, `tamamlandi_mi (is_completed)`, `seans_tarihi`

### 🎨 Modül 3: Tutarlılık İncilleri (Bibles)
* **`karakter_rehberi (character_bibles)`:** Karakterin sahneler boyunca aynı kalmasını sağlayan sabitler.
  * `id` [PK], `cocuk_id (child_id)` [FK -> cocuk_profilleri, NULL ise genel sistem şablonu], `ad (name)`, `tur (species)`, `kisilik_ozellikleri`, `gorsel_capa_tokenleri (visual_anchor_tokens)`, `kiyafet_tanimi`, `ses_kimligi (voice_id)`, `temel_seed (base_seed)`, `referans_gorsel_url`, `sistem_sablonu_mu`
* **`mekan_rehberi (location_bibles)`:** Arka planın ve doğanın sahneler boyunca tutarlı kalması.
  * `id` [PK], `ad (name)`, `ortam_turu (environment_type)`, `atmosfer_tanimi`, `isik_stili (lighting_style)`, `kalici_isaretler (recurring_landmarks)`, `gorsel_capa_tokenleri`, `referans_gorsel_url`, `temel_seed`, `sistem_sablonu_mu`

### 📖 Modül 4: Masal ve Dallanma Motoru
* **`masallar (stories)`:** Masalların ana kaydı (kesinlikle tek bir `cocuk_id`'ye bağlıdır).
  * `id` [PK], `cocuk_id` [FK -> cocuk_profilleri], `ebeveyn_id` [FK -> ebeveynler], `ana_karakter_id` [FK -> karakter_rehberi], `ana_mekan_id` [FK -> mekan_rehberi], `baslik (title)`, `tema (theme)`, `masal_modu (SINGLE_SHOT / INTERACTIVE)`, `kisisellestirildi_mi`, `toplam_sahne_sayisi (4-6)`, `durum (PENDING / GENERATING / COMPLETED)`, `kapak_gorseli_url`, `saklama_son_tarih`, `favori_mi`
* **`masal_sahneleri (story_scenes)`:** 4-6 sahnelik metin, WebP görsel, MP3 ses ve kelime zaman damgaları.
  * `id` [PK], `masal_id` [FK -> masallar], `mekan_id` [FK -> mekan_rehberi], `sahne_sirasi`, `sahne_metni`, `ses_metni`, `gorsel_promptu`, `negatif_prompt`, `gorsel_url`, `ses_url`, `ses_suresi_sn`, `karaoke_zaman_damgalari (timestamps_json)`, `uretim_seed`
* **`etkilesimli_dugumler (interactive_nodes)`:** Çocuğun masal akışını yönettiği A/B karar noktaları.
  * `id` [PK], `masal_id` [FK -> masallar], `sahne_id` [FK -> masal_sahneleri], `karar_adimi`, `sorulan_soru`, `secenek_a_metin`, `secenek_a_ikon`, `secenek_b_metin`, `secenek_b_ikon`, `secilen_yol`

### 💳 Modül 5: Abonelik ve Kredi Sistemi
* **`abonelik_planlari (subscription_plans)`:** Ücretsiz, Standart ve Aile VIP paket tanımları.
  * `id` [PK], `plan_kodu` [UQ], `ad`, `fiyat`, `aylik_masal_kredisi`, `etkilesimli_kredi`, `maks_cocuk_sayisi`, `saklama_suresi_gun (7 / 180 / 3650)`, `aktif_mi`
* **`ebeveyn_abonelikleri (parent_subscriptions)`:** Ebeveynin aktif abonelik durumu.
  * `id` [PK], `ebeveyn_id` [FK -> ebeveynler], `plan_id` [FK -> abonelik_planlari], `durum`, `baslangic_tarihi`, `bitis_tarihi`, `oto_yenileme`
* **`kredi_cuzdanlari (credit_wallets)`:** Ebeveynin normal ve etkileşimli masal bakiyesi.
  * `id` [PK], `ebeveyn_id` [UQ, FK -> ebeveynler (Birebir 1-1)], `masal_kredisi_bakiye`, `etkilesimli_kredi_bakiye`, `guncellendi_tarih`
* **`kredi_hareketleri (credit_transactions)`:** Kredi harcama, yükleme ve iade kayıtları.
  * `id` [PK], `cuzdan_id` [FK -> kredi_cuzdanlari], `islem_turu`, `masal_kredi_miktari`, `etkilesimli_kredi_miktari`, `aciklama`, `olusturuldu_tarih`

### ⚙️ Modül 6: Sistem Parametreleri, Güvenlik ve Ebeveyn Kilidi
* **`sistem_ayarlari (system_settings)`:** Kod değiştirmeden yönetilen ayarlar.
  * `id` [PK], `ayar_anahtari` [UQ], `ayar_degeri`, `veri_tipi`, `kategori`, `aciklama`, `guncellendi_tarih`
* **`yapay_zeka_saglayicilari (ai_providers_config)`:** Dış AI servisleri (Gemini, Groq, Cloudflare AI, ElevenLabs) öncelik ve timeout ayarları.
  * `id` [PK], `servis_turu`, `saglayici_adi`, `aktif_model_adi`, `aktif_mi`, `oncelik_sirasi (1: Birincil, 2: Yedek)`, `zaman_asimi_ms`, `maliyet_1k_token`
* **`prompt_sablonlari (prompt_templates)`:** Masal üretim promptlarının versiyonlu yönetimi.
  * `id` [PK], `sablon_anahtari` [UQ], `sistem_promptu`, `kullanici_prompt_sablonu`, `aktif_mi`, `guncellendi_tarih`
* **`yasakli_icerikler (content_blacklists)`:** Pedagojik güvenlik kara listesi (korku, şiddet, ayrılık anksiyetesi kelimeleri).
  * `id` [PK], `yasakli_kelime` [UQ], `kategori`, `uygulanacak_aksiyon`, `ikame_kelime`, `aktif_mi`
* **`yonetici_denetim_izleri (admin_audit_logs)`:** Yöneticilerin yaptığı kritik işlemlerin güvenlik logları.
  * `id` [PK], `yonetici_id` [FK -> yoneticiler], `islem_turu`, `hedef_tablo`, `hedef_id`, `detaylar`, `ip_adresi`, `olusturuldu_tarih`
* **`ebeveyn_kilidi_kayitlari (parental_gate_attempts)`:** Çocukların yanlışlıkla kredi harcamasını veya ayarları açmasını önleyen kilit kayıtları.
  * `id` [PK], `ebeveyn_id` [FK -> ebeveynler], `sorulan_soru`, `basarili_mi`, `hedef_islem`, `ip_adresi`, `deneme_tarihi`

---

## 3. Tablolar Arası İlişki Haritası (Relationship Map)

* `roller` ──▶ `yoneticiler` (1-e-Çok, `rol_id`)
* `roller` ──▶ `ebeveynler` (1-e-Çok, `rol_id`)
* `ebeveynler` ──▶ `cocuk_profilleri` (1-e-Çok, `ebeveyn_id`)
* `ebeveynler` ──▶ `kredi_cuzdanlari` (Birebir 1-1, `ebeveyn_id`)
* `ebeveynler` ──▶ `ebeveyn_abonelikleri` (1-e-Çok, `ebeveyn_id`)
* `abonelik_planlari` ──▶ `ebeveyn_abonelikleri` (1-e-Çok, `plan_id`)
* `kredi_cuzdanlari` ──▶ `kredi_hareketleri` (1-e-Çok, `cuzdan_id`)
* `ebeveynler` ──▶ `ebeveyn_kilidi_kayitlari` (1-e-Çok, `ebeveyn_id`)
* `cocuk_profilleri` ──▶ `masallar` (1-e-Çok, `cocuk_id` - **Veri İzolasyonu**)
* `cocuk_profilleri` ──▶ `karakter_rehberi` (1-e-Çok, `cocuk_id`)
* `cocuk_profilleri` ──▶ `okuma_seanslari` (1-e-Çok, `cocuk_id`)
* `karakter_rehberi` ──▶ `masallar` (1-e-Çok, `ana_karakter_id` - **Karakter Tutarlılığı**)
* `mekan_rehberi` ──▶ `masallar` (1-e-Çok, `ana_mekan_id` - **Mekân Tutarlılığı**)
* `masallar` ──▶ `masal_sahneleri` (1-e-Çok, `masal_id`)
* `masallar` ──▶ `etkilesimli_dugumler` (1-e-Çok, `masal_id` - **Karar Ağacı**)
* `yoneticiler` ──▶ `yonetici_denetim_izleri` (1-e-Çok, `yonetici_id`)

---

## 4. Proje Tanımına Göre Şema Güncellemeleri

Güncel ve bağlayıcı şema Flyway dosyalarıdır: `backend/src/main/resources/db/migration`. Yukarıdaki 20 tablo korunmuş, aşağıdaki değişiklikler yapılmıştır. Toplam 29 tablo vardır.

### Eklenen tablolar

| Tablo | Neden gerekli |
| :--- | :--- |
| `story_characters` | Ebeveyn yan karakter seçebilir |
| `story_favorites` | Keşfetteki masallar başka ebeveynlerce de favorilenebilir |
| `story_moderation_reviews` | Herkese açık masalların otomatik ve yönetici denetim geçmişi |
| `generation_jobs` | Metin, görsel ve ses için asenkron üretim, yeniden deneme ve hata takibi |
| `account_tokens` | E-posta doğrulama ve parola sıfırlama |
| `refresh_tokens` | Mobil uygulamada kalıcı oturum (Stateless JWT kararının tamamlayıcısı) |
| `parent_consents` | Ticari ürün için KVKK ve ebeveyn onayı kanıtı |
| `chat_sessions` | Final: masal sonrası sohbet oturumu |
| `chat_messages` | Final: sohbet mesajları ve işaretleme |

### Değişen sütunlar

| Tablo | Değişiklik | Neden |
| :--- | :--- | :--- |
| `child_profiles` | Yaş 3-7; `avatar_url` yerine `avatar_key` | Çocuk verisi dış avatar servislerine gönderilmez |
| `stories` | `narration_style`, `target_duration_minutes` eklendi | Ebeveyn anlatım biçimi ve süre seçer |
| `stories` | `visibility`, `moderation_status`, `published_at` eklendi | Özel, herkese açık ve denetim akışı |
| `stories` | `is_favorite` kaldırıldı | Favori artık ebeveyne özeldir |
| `stories` | Veritabanı kısıtı eklendi | Onaysız masal yayımlanamaz |
| `credit_wallets` | `version` ve negatif bakiye kısıtı | Eşzamanlı çift harcama engeli |
| `credit_transactions` | `story_id` eklendi | Harcama ve iade masala bağlanır |
| `parent_subscriptions` | `end_date` boş olabilir | Süresiz ücretsiz plan |

Ücretli planların fiyatı henüz belirlenmediği için `STANDARD_MONTHLY` ve `FAMILY_VIP` kapalı başlar.
