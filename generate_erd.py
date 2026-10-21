import os
from PIL import Image, ImageDraw, ImageFont

# Canvas dimensions
WIDTH = 4000
HEIGHT = 2500
BG_COLOR = (248, 250, 252)  # Slate 50

# Fonts with Turkish character support
FONT_BOLD_PATH = "C:/Windows/Fonts/segoeuib.ttf"
FONT_REG_PATH = "C:/Windows/Fonts/segoeui.ttf"

title_font = ImageFont.truetype(FONT_BOLD_PATH, 42)
subtitle_font = ImageFont.truetype(FONT_REG_PATH, 22)
tbl_header_font = ImageFont.truetype(FONT_BOLD_PATH, 20)
tbl_sub_font = ImageFont.truetype(FONT_REG_PATH, 14)
col_font = ImageFont.truetype(FONT_BOLD_PATH, 15)
type_font = ImageFont.truetype(FONT_REG_PATH, 13)
desc_font = ImageFont.truetype(FONT_REG_PATH, 13)
badge_font = ImageFont.truetype(FONT_BOLD_PATH, 12)

img = Image.new("RGB", (WIDTH, HEIGHT), BG_COLOR)
draw = ImageDraw.Draw(img)

# Title Header
draw.rectangle([(0, 0), (WIDTH, 140)], fill=(15, 23, 42))  # Slate 900
draw.text((80, 25), "3-6 YAŞ YAPAY ZEKÂ MASAL PLATFORMU - VERİTABANI İLİŞKİ ŞEMASI (ERD)", font=title_font, fill=(255, 255, 255))
draw.text((80, 85), "Faz 1 (MVP) Optimize Edilmiş 20 Tablo, Sütunlar, Veri Tipleri ve Tablolar Arası İlişkiler (Tamamı Türkçe)", font=subtitle_font, fill=(148, 163, 184))

# Category Colors
COLORS = {
    "auth": {"header": (37, 99, 235), "bg": (239, 246, 255), "border": (191, 219, 254), "tag": "KİMLİK DOĞRULAMA & YÖNETİM"},
    "child": {"header": (16, 185, 129), "bg": (240, 253, 244), "border": (187, 247, 208), "tag": "ÇOCUK PROFİLİ & İZOLASYON"},
    "bible": {"header": (147, 51, 234), "bg": (250, 245, 255), "border": (233, 213, 255), "tag": "TUTARLILIK İNCİLLERİ (BIBLES)"},
    "story": {"header": (234, 88, 12), "bg": (255, 247, 237), "border": (254, 215, 170), "tag": "MASAL VE DALLANMA MOTORU"},
    "billing": {"header": (202, 138, 4), "bg": (254, 252, 232), "border": (254, 240, 138), "tag": "ABONELİK VE KREDİ SİSTEMİ"},
    "admin": {"header": (225, 29, 72), "bg": (255, 241, 242), "border": (254, 205, 211), "tag": "SİSTEM VE GÜVENLİK AYARLARI"}
}

# 20 Tables categorized
tables = [
    # Sütun 1: Kimlik & Faturalandırma
    {
        "id": "roles", "name": "roller", "orig": "roles", "cat": "auth",
        "x": 80, "y": 180, "w": 460,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Benzersiz Rol ID"),
            ("rol_kodu", "VARCHAR(50)", "UQ", "ROLE_PARENT / ROLE_ADMIN"),
            ("gorunen_ad", "VARCHAR(100)", "", "Kullanıcı Dostu Başlık"),
            ("aciklama", "VARCHAR(255)", "", "Rolün Kapsamı ve Yetkileri"),
            ("olusturuldu_tarih", "TIMESTAMPTZ", "", "Kayıt Zamanı")
        ]
    },
    {
        "id": "admins", "name": "yoneticiler", "orig": "admins", "cat": "auth",
        "x": 80, "y": 450, "w": 460,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Yönetici ID"),
            ("kullanici_adi", "VARCHAR(100)", "UQ", "Giriş Kullanıcı Adı"),
            ("eposta", "VARCHAR(255)", "UQ", "İletişim E-Postası"),
            ("sifre_hash", "VARCHAR(255)", "", "BCrypt Şifreleme"),
            ("ad_soyad", "VARCHAR(150)", "", "Yönetici Adı Soyadı"),
            ("rol_id", "BIGINT", "FK", "roller(id) -> Rol ID"),
            ("aktif_mi", "BOOLEAN", "", "Hesap Durumu"),
            ("son_giris_tarih", "TIMESTAMPTZ", "", "Son Oturum Zamanı")
        ]
    },
    {
        "id": "admin_audit_logs", "name": "yonetici_denetim_izleri", "orig": "admin_audit_logs", "cat": "admin",
        "x": 80, "y": 820, "w": 460,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Denetim Kayıt ID"),
            ("yonetici_id", "BIGINT", "FK", "yoneticiler(id)"),
            ("islem_turu", "VARCHAR(100)", "", "Yapılan İdari İşlem"),
            ("hedef_tablo", "VARCHAR(100)", "", "Etkilenen Tablo Adı"),
            ("hedef_id", "BIGINT", "", "Etkilenen Kayıt No"),
            ("detaylar", "TEXT", "", "İşlem Detayları"),
            ("ip_adresi", "VARCHAR(45)", "", "Yönetici Cihaz IP'si"),
            ("olusturuldu_tarih", "TIMESTAMPTZ", "", "İşlem Zamanı")
        ]
    },
    {
        "id": "subscription_plans", "name": "abonelik_planlari", "orig": "subscription_plans", "cat": "billing",
        "x": 80, "y": 1210, "w": 460,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Paket ID"),
            ("plan_kodu", "VARCHAR(50)", "UQ", "FREE / STANDARD / VIP"),
            ("ad", "VARCHAR(100)", "", "Paket Satış Adı"),
            ("fiyat", "DECIMAL(10,2)", "", "Aylık/Yıllık Ücret"),
            ("aylik_masal_kredisi", "INT", "", "Normal Masal Hakkı"),
            ("etkilesimli_kredi", "INT", "", "Etkileşimli Masal Hakkı"),
            ("maks_cocuk_sayisi", "INT", "", "Açılabilecek Çocuk Profili"),
            ("saklama_suresi_gun", "INT", "", "Bulut Saklama (7/180/3650 gün)"),
            ("aktif_mi", "BOOLEAN", "", "Paket Satışta mı?")
        ]
    },
    {
        "id": "parent_subscriptions", "name": "ebeveyn_abonelikleri", "orig": "parent_subscriptions", "cat": "billing",
        "x": 80, "y": 1650, "w": 460,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Abonelik ID"),
            ("ebeveyn_id", "BIGINT", "FK", "ebeveynler(id)"),
            ("plan_id", "BIGINT", "FK", "abonelik_planlari(id)"),
            ("durum", "VARCHAR(50)", "", "ACTIVE / CANCELLED"),
            ("baslangic_tarihi", "TIMESTAMPTZ", "", "Başlama Zamanı"),
            ("bitis_tarihi", "TIMESTAMPTZ", "", "Yenilenme / Bitiş"),
            ("oto_yenileme", "BOOLEAN", "", "Otomatik Çekim Açık mı?")
        ]
    },

    # Sütun 2: Ebeveyn & Cüzdan & Ebeveyn Kilidi
    {
        "id": "parents", "name": "ebeveynler", "orig": "parents", "cat": "auth",
        "x": 600, "y": 180, "w": 470,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Ebeveyn ID"),
            ("eposta", "VARCHAR(255)", "UQ", "Giriş E-Posta Adresi"),
            ("sifre_hash", "VARCHAR(255)", "", "BCrypt Parola Özeti"),
            ("ad", "VARCHAR(100)", "", "Ebeveyn Adı"),
            ("soyad", "VARCHAR(100)", "", "Ebeveyn Soyadı"),
            ("telefon", "VARCHAR(30)", "", "İletişim Numarası"),
            ("rol_id", "BIGINT", "FK", "roller(id) -> ROLE_PARENT"),
            ("tercih_edilen_dil", "VARCHAR(10)", "", "Dil Tercihi ('tr', 'en')"),
            ("avatar_url", "VARCHAR(500)", "", "Profil Resmi Bağlantısı"),
            ("aktif_mi", "BOOLEAN", "", "Hesap Durumu"),
            ("eposta_onayli_mi", "BOOLEAN", "", "E-Posta Doğrulandı mı?"),
            ("olusturuldu_tarih", "TIMESTAMPTZ", "", "Kayıt Tarihi")
        ]
    },
    {
        "id": "credit_wallets", "name": "kredi_cuzdanlari", "orig": "credit_wallets", "cat": "billing",
        "x": 600, "y": 680, "w": 470,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Cüzdan ID"),
            ("ebeveyn_id", "BIGINT", "UQ, FK", "ebeveynler(id) [Birebir 1-1]"),
            ("masal_kredisi_bakiye", "INT", "", "Mevcut Standart Masal Kredisi"),
            ("etkilesimli_kredi_bakiye", "INT", "", "Mevcut Dallanan Masal Kredisi"),
            ("guncellendi_tarih", "TIMESTAMPTZ", "", "Son Bakiye Değişimi")
        ]
    },
    {
        "id": "credit_transactions", "name": "kredi_hareketleri", "orig": "credit_transactions", "cat": "billing",
        "x": 600, "y": 970, "w": 470,
        "cols": [
            ("id", "BIGSERIAL", "PK", "İşlem No"),
            ("cuzdan_id", "BIGINT", "FK", "kredi_cuzdanlari(id)"),
            ("islem_turu", "VARCHAR(50)", "", "PLAN_YUKLEME / HARCAMA"),
            ("masal_kredi_miktari", "INT", "", "Standart Kredi Değişimi (+/-)"),
            ("etkilesimli_kredi_miktari", "INT", "", "Etkileşimli Değişimi (+/-)"),
            ("aciklama", "VARCHAR(255)", "", "İşlem Sebebi / Masal Adı"),
            ("olusturuldu_tarih", "TIMESTAMPTZ", "", "İşlem Zamanı")
        ]
    },
    {
        "id": "parental_gate_attempts", "name": "ebeveyn_kilidi_kayitlari", "orig": "parental_gate_attempts", "cat": "admin",
        "x": 600, "y": 1330, "w": 470,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Kilit Deneme ID"),
            ("ebeveyn_id", "BIGINT", "FK", "ebeveynler(id)"),
            ("sorulan_soru", "VARCHAR(100)", "", "Örn: 'DÖRT yazan kutu', '4x3'"),
            ("basarili_mi", "BOOLEAN", "", "Çocuk mu Ebeveyn mi Doğrulandı"),
            ("hedef_islem", "VARCHAR(100)", "", "MASAL_URET / KREDI_AL"),
            ("ip_adresi", "VARCHAR(45)", "", "İstek IP Bilgisi"),
            ("deneme_tarihi", "TIMESTAMPTZ", "", "İşlem Zamanı")
        ]
    },

    # Sütun 3: Çocuk & Okuma Seansları & Tutarlılık İncilleri
    {
        "id": "child_profiles", "name": "cocuk_profilleri", "orig": "child_profiles", "cat": "child",
        "x": 1130, "y": 180, "w": 480,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Çocuk Profil ID"),
            ("ebeveyn_id", "BIGINT", "FK", "ebeveynler(id)"),
            ("ad", "VARCHAR(100)", "", "Çocuğun Adı"),
            ("yas", "INT", "", "Yaş (2-8 Arası Sınırı)"),
            ("cinsiyet", "VARCHAR(20)", "", "Cinsiyet Bilgisi"),
            ("sevdigi_hayvanlar", "TEXT", "", "Kişiselleştirme: Hayvanlar"),
            ("sevdigi_renkler", "TEXT", "", "Kişiselleştirme: Renkler"),
            ("ozel_ilgi_alanlari", "TEXT", "", "Dinozor, Uzay, Boyama vb."),
            ("uyku_saati", "TIME", "", "Uyku Masalı Zamanı"),
            ("avatar_url", "VARCHAR(500)", "", "Seçilen Avatar Çizimi"),
            ("aktif_mi", "BOOLEAN", "", "Profil Aktif mi?"),
            ("olusturuldu_tarih", "TIMESTAMPTZ", "", "Oluşturulma Zamanı")
        ]
    },
    {
        "id": "child_reading_sessions", "name": "okuma_seanslari", "orig": "child_reading_sessions", "cat": "child",
        "x": 1130, "y": 680, "w": 480,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Seans ID"),
            ("cocuk_id", "BIGINT", "FK", "cocuk_profilleri(id)"),
            ("masal_id", "BIGINT", "FK", "masallar(id)"),
            ("dinleme_suresi_sn", "INT", "", "Toplam Dinleme Süresi (sn)"),
            ("tamamlandi_mi", "BOOLEAN", "", "Masal Sonu Görüldü mü?"),
            ("seans_tarihi", "TIMESTAMPTZ", "", "Dinleme Saati")
        ]
    },
    {
        "id": "character_bibles", "name": "karakter_rehberi", "orig": "character_bibles", "cat": "bible",
        "x": 1130, "y": 990, "w": 480,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Karakter ID"),
            ("cocuk_id", "BIGINT", "FK", "cocuk_profilleri(id) [Özel/Genel]"),
            ("ad", "VARCHAR(100)", "", "Karakter Adı ('Fil Fikri')"),
            ("tur", "VARCHAR(50)", "", "Türü ('Bebek Fil', 'Tavşan')"),
            ("kisilik_ozellikleri", "TEXT", "", "Neşeli, Meraklı, Yardımsever"),
            ("gorsel_capa_tokenleri", "TEXT", "", "GÖRSEL ÇAPA: 'cute pastel elephant'"),
            ("kiyafet_tanimi", "TEXT", "", "Sabit Giysi ('Yeşil sırt çantası')"),
            ("ses_kimligi", "VARCHAR(100)", "", "TTS Voice ID (ElevenLabs/Azure)"),
            ("temel_seed", "BIGINT", "", "Görsel Sabit Tohumu (Seed)"),
            ("referans_gorsel_url", "VARCHAR(500)", "", "Model Sheet Referans Görseli"),
            ("sistem_sablonu_mu", "BOOLEAN", "", "Hazır Sistem Karakteri mi?")
        ]
    },
    {
        "id": "location_bibles", "name": "mekan_rehberi", "orig": "location_bibles", "cat": "bible",
        "x": 1130, "y": 1470, "w": 480,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Mekân ID"),
            ("ad", "VARCHAR(150)", "", "Mekân Adı ('Papatya Ormanı')"),
            ("ortam_turu", "VARCHAR(100)", "", "Orman, Şato, Denizaltı"),
            ("atmosfer_tanimi", "TEXT", "", "Ilık pastel, tatlı gün ışığı"),
            ("isik_stili", "VARCHAR(100)", "", "Soft morning sun, no darkness"),
            ("kalici_isaretler", "TEXT", "", "Arka plandaki mor nehir ve köprü"),
            ("gorsel_capa_tokenleri", "TEXT", "", "SABİT MEKÂN PROMPT TANIMI"),
            ("referans_gorsel_url", "VARCHAR(500)", "", "Mekân Konsept Çizim URL'si"),
            ("temel_seed", "BIGINT", "", "Mekân Tohum Değeri"),
            ("sistem_sablonu_mu", "BOOLEAN", "", "Hazır Sistem Mekânı mı?")
        ]
    },

    # Sütun 4: Masal ve Dallanma Motoru
    {
        "id": "stories", "name": "masallar", "orig": "stories", "cat": "story",
        "x": 1670, "y": 180, "w": 490,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Masal ID"),
            ("cocuk_id", "BIGINT", "FK", "cocuk_profilleri(id) [İZOLASYON]"),
            ("ebeveyn_id", "BIGINT", "FK", "ebeveynler(id)"),
            ("ana_karakter_id", "BIGINT", "FK", "karakter_rehberi(id)"),
            ("ana_mekan_id", "BIGINT", "FK", "mekan_rehberi(id)"),
            ("baslik", "VARCHAR(255)", "", "Masal Başlığı"),
            ("tema", "VARCHAR(150)", "", "Tema ('Paylaşmak', 'Dostluk')"),
            ("masal_modu", "VARCHAR(50)", "", "SINGLE_SHOT / INTERACTIVE"),
            ("kisisellestirildi_mi", "BOOLEAN", "", "Çocuk Bilgileri Eklendi mi?"),
            ("toplam_sahne_sayisi", "INT", "", "Sahne Sayısı (4 - 6)"),
            ("durum", "VARCHAR(50)", "", "PENDING / GENERATING / COMPLETED"),
            ("kapak_gorseli_url", "VARCHAR(500)", "", "Kapak Çizimi"),
            ("saklama_son_tarih", "TIMESTAMPTZ", "", "Buluttan Silinme Tarihi"),
            ("favori_mi", "BOOLEAN", "", "Beğenilen Masal mı?")
        ]
    },
    {
        "id": "story_scenes", "name": "masal_sahneleri", "orig": "story_scenes", "cat": "story",
        "x": 1670, "y": 760, "w": 490,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Sahne ID"),
            ("masal_id", "BIGINT", "FK", "masallar(id)"),
            ("mekan_id", "BIGINT", "FK", "mekan_rehberi(id)"),
            ("sahne_sirasi", "INT", "", "Sahne Numarası (1, 2, 3...)"),
            ("sahne_metni", "TEXT", "", "Ekranda Yazan Metin (1-2 Cümle)"),
            ("ses_metni", "TEXT", "", "Seslendirmenin Okuduğu Metin"),
            ("gorsel_promptu", "TEXT", "", "FLUX/SD Görsel Üretim Prompt'u"),
            ("negatif_prompt", "TEXT", "", "Korku/Karanlık Önleyici Filtre"),
            ("gorsel_url", "VARCHAR(500)", "", "Üretilen WebP İllüstrasyon"),
            ("ses_url", "VARCHAR(500)", "", "Üretilen MP3 Seslendirme"),
            ("ses_suresi_sn", "DECIMAL(6,2)", "", "Ses Uzunluğu (Saniye)"),
            ("karaoke_zaman_damgalari", "JSONB", "", "KARAOKE: Kelime Başlangıç/Bitiş"),
            ("uretim_seed", "BIGINT", "", "Çizim Tohum Değeri")
        ]
    },
    {
        "id": "interactive_nodes", "name": "etkilesimli_dugumler", "orig": "interactive_nodes", "cat": "story",
        "x": 1670, "y": 1330, "w": 490,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Düğüm ID"),
            ("masal_id", "BIGINT", "FK", "masallar(id)"),
            ("sahne_id", "BIGINT", "FK", "masal_sahneleri(id)"),
            ("karar_adimi", "INT", "", "Karar Noktası (1. veya 2. Seçim)"),
            ("sorulan_soru", "VARCHAR(255)", "", "'Mavi kapı mı, sarı patika mı?'"),
            ("secenek_a_metin", "VARCHAR(100)", "", "1. Seçenek Metni"),
            ("secenek_a_ikon", "VARCHAR(100)", "", "1. Seçenek Simgesi"),
            ("secenek_b_metin", "VARCHAR(100)", "", "2. Seçenek Metni"),
            ("secenek_b_ikon", "VARCHAR(100)", "", "2. Seçenek Simgesi"),
            ("secilen_yol", "VARCHAR(10)", "", "Çocuğun Seçimi ('A' veya 'B')")
        ]
    },

    # Sütun 5: Sistem Parametreleri & Yapay Zekâ & Güvenlik
    {
        "id": "system_settings", "name": "sistem_ayarlari", "orig": "system_settings", "cat": "admin",
        "x": 2220, "y": 180, "w": 480,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Ayar ID"),
            ("ayar_anahtari", "VARCHAR(100)", "UQ", "DEFAULT_AI_MODEL vb."),
            ("ayar_degeri", "TEXT", "", "Ayar Değeri"),
            ("veri_tipi", "VARCHAR(30)", "", "STRING / INTEGER / BOOLEAN"),
            ("kategori", "VARCHAR(50)", "", "AI_CONFIG / BILLING / SAFETY"),
            ("aciklama", "VARCHAR(255)", "", "Ayarın İşlevi"),
            ("guncellendi_tarih", "TIMESTAMPTZ", "", "Son Düzenleme")
        ]
    },
    {
        "id": "ai_providers_config", "name": "yapay_zeka_saglayicilari", "orig": "ai_providers_config", "cat": "admin",
        "x": 2220, "y": 550, "w": 480,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Sağlayıcı ID"),
            ("servis_turu", "VARCHAR(50)", "", "LLM / IMAGE / TTS"),
            ("saglayici_adi", "VARCHAR(100)", "", "GEMINI / GROQ / CLOUDFLARE"),
            ("aktif_model_adi", "VARCHAR(150)", "", "'gemini-2.5-flash', 'flux'"),
            ("aktif_mi", "BOOLEAN", "", "Servis Devrede mi?"),
            ("oncelik_sirasi", "INT", "", "Yedeklilik (1: Birincil, 2: Yedek)"),
            ("zaman_asimi_ms", "INT", "", "Maksimum Bekleme (ms)"),
            ("maliyet_1k_token", "DECIMAL(8,6)", "", "Birim Maliyet Takibi")
        ]
    },
    {
        "id": "prompt_templates", "name": "prompt_sablonlari", "orig": "prompt_templates", "cat": "admin",
        "x": 2220, "y": 950, "w": 480,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Şablon ID"),
            ("sablon_anahtari", "VARCHAR(100)", "UQ", "STORY_GEN_3_6 vb."),
            ("sistem_promptu", "TEXT", "", "Pedagojik Güvenlik Şablonu"),
            ("kullanici_prompt_sablonu", "TEXT", "", "Dinamik Değişkenli Prompt"),
            ("aktif_mi", "BOOLEAN", "", "Kullanımda mı?"),
            ("guncellendi_tarih", "TIMESTAMPTZ", "", "Son Güncelleme")
        ]
    },
    {
        "id": "content_blacklists", "name": "yasakli_icerikler", "orig": "content_blacklists", "cat": "admin",
        "x": 2220, "y": 1310, "w": 480,
        "cols": [
            ("id", "BIGSERIAL", "PK", "Kural ID"),
            ("yasakli_kelime", "VARCHAR(100)", "UQ", "Filtrelenecek Kelime Kökü"),
            ("kategori", "VARCHAR(50)", "", "KORKU / ŞİDDET / ANKSİYETE"),
            ("uygulanacak_aksiyon", "VARCHAR(50)", "", "OTOMATİK_RED / SANSÜRLEME"),
            ("ikame_kelime", "VARCHAR(100)", "", "Yerine Geçecek Sevimli İfade"),
            ("aktif_mi", "BOOLEAN", "", "Filtre Devrede mi?")
        ]
    }
]

# Draw Table Cards
for t in tables:
    x, y, w = t["x"], t["y"], t["w"]
    cols = t["cols"]
    header_h = 60
    row_h = 30
    total_h = header_h + len(cols) * row_h + 12
    
    cfg = COLORS[t["cat"]]
    
    # Outer Card Background & Shadow
    draw.rounded_rectangle([(x+4, y+4), (x + w + 4, y + total_h + 4)], radius=12, fill=(226, 232, 240))
    draw.rounded_rectangle([(x, y), (x + w, y + total_h)], radius=12, fill=(255, 255, 255), outline=cfg["border"], width=2)
    
    # Table Header
    draw.rounded_rectangle([(x, y), (x + w, y + header_h)], radius=10, fill=cfg["header"])
    draw.rectangle([(x, y + header_h - 10), (x + w, y + header_h)], fill=cfg["header"])
    
    # Header Text
    draw.text((x + 16, y + 8), t["name"], font=tbl_header_font, fill=(255, 255, 255))
    draw.text((x + 16, y + 34), f"SQL: {t['orig']}", font=tbl_sub_font, fill=(224, 231, 255))
    
    # Category tag on header right
    tag_w = draw.textlength(cfg["tag"], font=badge_font)
    draw.text((x + w - tag_w - 14, y + 12), cfg["tag"], font=badge_font, fill=(255, 255, 255, 220))
    
    # Column Rows
    curr_y = y + header_h + 6
    for idx, (col_name, col_type, key_type, desc) in enumerate(cols):
        if idx % 2 == 1:
            draw.rectangle([(x + 2, curr_y - 2), (x + w - 2, curr_y + row_h - 2)], fill=cfg["bg"])
            
        badge_x = x + 12
        if key_type == "PK":
            draw.rounded_rectangle([(badge_x, curr_y + 1), (badge_x + 28, curr_y + 20)], radius=4, fill=(220, 38, 38))
            draw.text((badge_x + 6, curr_y + 3), "PK", font=badge_font, fill=(255, 255, 255))
        elif "FK" in key_type:
            draw.rounded_rectangle([(badge_x, curr_y + 1), (badge_x + 28, curr_y + 20)], radius=4, fill=(37, 99, 235))
            draw.text((badge_x + 6, curr_y + 3), "FK", font=badge_font, fill=(255, 255, 255))
        elif key_type == "UQ":
            draw.rounded_rectangle([(badge_x, curr_y + 1), (badge_x + 28, curr_y + 20)], radius=4, fill=(13, 148, 136))
            draw.text((badge_x + 5, curr_y + 3), "UQ", font=badge_font, fill=(255, 255, 255))
        else:
            draw.text((badge_x + 8, curr_y + 2), "-", font=col_font, fill=(148, 163, 184))
            
        draw.text((x + 48, curr_y + 2), col_name, font=col_font, fill=(15, 23, 42))
        
        type_w = draw.textlength(col_type, font=type_font)
        draw.text((x + 245, curr_y + 4), col_type, font=type_font, fill=(100, 116, 139))
        draw.text((x + 245 + type_w + 8, curr_y + 4), desc[:24], font=desc_font, fill=(148, 163, 184))
        
        curr_y += row_h

# Relationships List
relations = [
    ("roller", "yoneticiler", "1-e-Çok (rol_id)", (37, 99, 235)),
    ("roller", "ebeveynler", "1-e-Çok (rol_id)", (37, 99, 235)),
    ("ebeveynler", "cocuk_profilleri", "1-e-Çok (ebeveyn_id)", (16, 185, 129)),
    ("ebeveynler", "kredi_cuzdanlari", "Birebir 1-1 (ebeveyn_id)", (202, 138, 4)),
    ("ebeveynler", "ebeveyn_abonelikleri", "1-e-Çok (ebeveyn_id)", (202, 138, 4)),
    ("abonelik_planlari", "ebeveyn_abonelikleri", "1-e-Çok (plan_id)", (202, 138, 4)),
    ("kredi_cuzdanlari", "kredi_hareketleri", "1-e-Çok (cuzdan_id)", (202, 138, 4)),
    ("ebeveynler", "ebeveyn_kilidi_kayitlari", "1-e-Çok (ebeveyn_id)", (225, 29, 72)),
    ("cocuk_profilleri", "masallar", "1-e-Çok (cocuk_id) [İZOLASYON]", (234, 88, 12)),
    ("cocuk_profilleri", "karakter_rehberi", "1-e-Çok (cocuk_id)", (147, 51, 234)),
    ("cocuk_profilleri", "okuma_seanslari", "1-e-Çok (cocuk_id)", (16, 185, 129)),
    ("karakter_rehberi", "masallar", "1-e-Çok (ana_karakter_id)", (147, 51, 234)),
    ("mekan_rehberi", "masallar", "1-e-Çok (ana_mekan_id)", (147, 51, 234)),
    ("masallar", "masal_sahneleri", "1-e-Çok (masal_id)", (234, 88, 12)),
    ("masallar", "etkilesimli_dugumler", "1-e-Çok (masal_id)", (234, 88, 12)),
    ("yoneticiler", "yonetici_denetim_izleri", "1-e-Çok (yonetici_id)", (225, 29, 72))
]

# Right Side Panel: Legend & Architecture
box_x = 2740
box_y = 180
box_w = 1180
box_h = 2220

draw.rounded_rectangle([(box_x + 4, box_y + 4), (box_x + box_w + 4, box_y + box_h + 4)], radius=16, fill=(226, 232, 240))
draw.rounded_rectangle([(box_x, box_y), (box_x + box_w, box_y + box_h)], radius=16, fill=(255, 255, 255), outline=(203, 213, 225), width=2)

draw.rounded_rectangle([(box_x, box_y), (box_x + box_w, box_y + 75)], radius=14, fill=(15, 23, 42))
draw.rectangle([(box_x, box_y + 65), (box_x + box_w, box_y + 75)], fill=(15, 23, 42))
draw.text((box_x + 24, box_y + 22), "TABLOLAR ARASI İLİŞKİLER VE KURALLAR (RELATIONSHIP MAP)", font=tbl_header_font, fill=(255, 255, 255))

legend_y = box_y + 100
legend_font = ImageFont.truetype(FONT_BOLD_PATH, 17)
desc_bold_font = ImageFont.truetype(FONT_BOLD_PATH, 15)
desc_font = ImageFont.truetype(FONT_REG_PATH, 14)

for src, dst, rel_name, col in relations:
    draw.rounded_rectangle([(box_x + 24, legend_y), (box_x + 36, legend_y + 32)], radius=4, fill=col)
    draw.text((box_x + 46, legend_y + 5), f"{src}  --->  {dst}", font=legend_font, fill=(15, 23, 42))
    draw.text((box_x + 550, legend_y + 6), rel_name, font=desc_bold_font, fill=col)
    legend_y += 46

legend_y += 20
draw.line([(box_x + 24, legend_y), (box_x + box_w - 24, legend_y)], fill=(226, 232, 240), width=2)
legend_y += 26

draw.text((box_x + 24, legend_y), "TEMEL MİMARİ VE PEDAGOJİK GÜVENCELER:", font=tbl_header_font, fill=(30, 41, 59))
legend_y += 42

notes = [
    ("1. ÇOCUK KİTAPLIĞI İZOLASYONU:", "Tüm masallar 'cocuk_id' ile filtrelenir. Çocuk 1'in masalı Çocuk 2'nin ekranında görünmez."),
    ("2. KARAKTER TUTARLILIĞI (BIBLE):", "Karakterin 'temel_seed' ve 'referans_gorsel_url' verileri tüm sahnelerde karakter kaymasını önler."),
    ("3. MEKÂN TUTARLILIĞI (BIBLE):", "Mekânın 'kalici_isaretler' ve 'isik_stili' sahneler boyu aynı masalsı evreni korur."),
    ("4. KARAOKE SES SENKRONİZASYONU:", "'masal_sahneleri.karaoke_zaman_damgalari' salise bazlı kelime vurgulama verisini JSONB tutar."),
    ("5. EBEVEYN KİLİDİ (PARENTAL GATE):", "Kredi harcama veya ayarları açma eylemleri 'ebeveyn_kilidi_kayitlari' doğrulamasıyla korunur."),
    ("6. ETKİLEŞİMLİ MASAL KARAR AĞACI:", "'etkilesimli_dugumler' tablosu çocuğun A/B seçimlerini dallanan düğüm yapısında saklar.")
]

for title, body in notes:
    draw.text((box_x + 24, legend_y), title, font=legend_font, fill=(37, 99, 235))
    legend_y += 24
    draw.text((box_x + 24, legend_y), body, font=desc_font, fill=(71, 85, 105))
    legend_y += 36

# Module Color Legend at bottom
legend_y += 20
draw.line([(box_x + 24, legend_y), (box_x + box_w - 24, legend_y)], fill=(226, 232, 240), width=2)
legend_y += 26
draw.text((box_x + 24, legend_y), "MODÜL VE RENK KODLARI:", font=tbl_header_font, fill=(30, 41, 59))
legend_y += 38

for k, cfg in COLORS.items():
    draw.rounded_rectangle([(box_x + 24, legend_y), (box_x + 50, legend_y + 22)], radius=4, fill=cfg["header"])
    draw.text((box_x + 60, legend_y + 2), cfg["tag"], font=desc_bold_font, fill=(15, 23, 42))
    legend_y += 32

output_png = "c:/Users/engin/Desktop/kids-histories/veritabani_sema_semasi.png"
img.save(output_png, quality=95)
print(f"PNG updated successfully: {output_png} ({os.path.getsize(output_png)} bytes)")
