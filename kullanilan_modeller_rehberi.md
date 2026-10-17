# 3-6 Yaş Yapay Zekâ Masal Platformu - Modeller, API'ler ve Mimari Rehberi

Bu rehber; 3-6 yaş grubu çocuklar için ebeveyn denetimli, sahneleme yapabilen, görsel tutarlılığı koruyan ve seslendirme desteğine sahip masal platformu için kullanılacak modelleri, API'leri, algoritmaları ve sistem mimarisini açık ve net bir şekilde özetler.

---

## 1. Hikaye Üretim ve Sahneleme Modelleri (LLM)

3-6 yaş grubu için dilin kısa, basit, kafiyeli/akıcı ve pedagojik olarak pozitif olması gerekir. Model yalnızca metin üretmez; çıktıyı doğrudan **JSON formatında** sahne sahne, her sahnenin görsel prompt'u ve seslendirme metniyle birlikte verir.

| Model / API | Avantajı | Kullanım Amacı | Önerilen Rol |
| :--- | :--- | :--- | :--- |
| **OpenAI GPT-4o-mini** | Çok hızlı, düşük maliyet, mükemmel JSON Schema desteği | Hikaye senaryosu ve sahne bölme | **Birincil Tercih** |
| **Google Gemini 1.5 Flash** | Çok dilli akıcılık (Türkçe anlatımda çok başarılı), düşük maliyet | Hikaye üretimi alternatifi | **Yedek / Alternatif** |
| **Anthropic Claude 3.5 Sonnet** | Üstün çocuk dili, duygu tonlaması ve pedagojik güvenlik | Premium/Uzun masallar | **Opsiyonel** |

### Örnek LLM JSON Çıktı Formatı:
```json
{
  "title": "Minik Fil Fikri'nin Balon Yolculuğu",
  "theme": "Paylaşmak ve Dostluk",
  "character_anchor": "Küçük mavi bebek fil Fikri, başında sarı papatya tacı var",
  "scenes": [
    {
      "scene_number": 1,
      "text": "Güneş gökyüzünde pırıl pırıl parlarken, minik fil Fikri neşeyle uyanmış.",
      "image_prompt": "A cute small pastel blue baby elephant with a yellow daisy crown on head, waking up smiling in a sunny whimsical meadow, soft watercolor children's book illustration, no text",
      "voice_text": "Güneş gökyüzünde pırıl pırıl parlarken, minik fil Fikri neşeyle uyanmış."
    }
  ]
}
```

---

## 2. Görsel Üretim Modelleri ve Karakter Tutarlılığı (Image Generation)

Çocuk masallarında en kritik problem **karakter kaymasıdır** (ana karakterin her sayfada farklı görünmesi).

| Model & Yöntem | Açıklama | Tutarlılık Gücü |
| :--- | :--- | :--- |
| **Fal.ai (FLUX.1 + IP-Adapter / PuLID)** | İlk sahnede oluşturulan karakter görselini referans alarak sonraki tüm sahnelere aktarır. API desteği çok hızlıdır. | ⭐⭐⭐⭐⭐ (En Yüksek) |
| **Midjourney v6 (`--cref` & `--cw`)** | Karakter referans URL'si vererek farklı açılardan aynı karakteri üretir. Sanatsal çocuk kitabı kalitesi benzersizdir. | ⭐⭐⭐⭐⭐ (En Yüksek Kalite) |
| **OpenAI DALL-E 3** | Kolay entegrasyon, yüksek prompt sadakati. Ancak sahneler arası karakter kayması yaşanabilir (stil promptu ile dengelenir). | ⭐⭐⭐ (Hızlı Başlangıç) |
| **Leonardo.ai Storybook API** | Karakter ve stil kilitleme (Character & Style Reference) doğrudan API üzerinden sunulur. | ⭐⭐⭐⭐ |

> **İllüstrasyon Stili Sabiti:** Tüm görsel üretim promptlarına şu stil ifadesi eklenmelidir:  
> `soft watercolor, whimsical children's book illustration, gentle pastel colors, clean lighting, masterpiece, no text, no gore`

---

## 3. Seslendirme ve Kelime Senkronizasyonu (TTS & Read-Along)

3-6 yaş okuma öncesi çocukların kelimeleri görsel ve işitsel olarak eşleştirmesi için **kelime düzeyinde zaman damgalı (Karaoke) seslendirme** gerekir.

| Sağlayıcı | Model / Özellik | Neden Kullanılmalı? |
| :--- | :--- | :--- |
| **ElevenLabs** | `/v1/text-to-speech/{voice_id}/with-timestamps` | Ses dosyasıyla birlikte her kelimenin salise başlangıç-bitiş zamanını verir. Masal anlatıcısı (Storyteller) kadın/erkek sesleri çok doğaldır. |
| **OpenAI Audio TTS** | `tts-1` / `alloy`, `nova` | Sıcak masal sesleri, ekonomik ve hızlı. (Kelime zamanlaması için Whisper forced-alignment ile desteklenebilir). |
| **Azure Speech Services** | Neural TTS (Türkçe Çocuk/Kadın Sesleri) | Zengin Türkçe ses profili ve hece/kelime seviyesinde viseme/zamanlama desteği. |

---

## 4. Güvenlik, Filtreleme ve Ebeveyn Denetimi (Safety & Parental Gate)

1. **İçerik Moderasyonu:**
   * **OpenAI Moderation API:** Ebeveynin girdiği serbest metinleri (örneğin tema veya karakter adı) anında tarar.
   * **Negatif Filtre:** Korku, canavar, karanlıkta kaybolma, şiddet, ebeveynden ayrılma temaları prompt aşamasında engellenir.
2. **Apple & Google Parental Gate Standardı:**
   * Çocukların yanlışlıkla masal oluşturmasını, kredi harcamasını veya harici linklere tıklamasını engellemek için **rastgele matematiksel/bilişsel kilit** kullanılır (Örn: *"Masal oluşturmak için: 4 + 3 = ?"* veya *"YEDİ yazan butona dokunun"*).
3. **COPPA / GDPR-K:**
   * Çocuklardan ad-soyad, gerçek fotoğraf veya kişisel veri (PII) toplanmaz.

---

## 5. Sistem ve Orkestrasyon Mimarisi

* **Mobil Uygulama (İstemci):** Flutter veya React Native
  * Sayfa çevirme efekti (Page Curl / Flip).
  * Ses oynarken kelime vurgulama (Karaoke highlighting).
  * Sevimli ses efektleri ve sakinleştirici fon müziği.
* **Backend API:** 
  * Hikaye üretimi, görselleştirme ve seslendirme asenkron kuyruk yapısı (Job Queue) ile çalışır.
  * Kullanıcı bekleme ekranında tatlı bir animasyon (örneğin masal sayfalarını boyayan bir sincap) görür; üretim bittiğinde masal anında açılır.
* **Depolama (Object Storage):**
  * Üretilen MP3 sesleri ve WebP/PNG görselleri Cloudflare R2 veya AWS S3 üzerinde barındırılır.
