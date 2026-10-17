# 3-6 Yaş Yapay Zekâ Masal Platformu Geliştirme Kuralları

Bu kurallar, projenin tüm backend, frontend, prompt tasarımı ve yapay zekâ entegrasyonu aşamalarında katı bir şekilde uygulanır:

1. **Pedagojik ve Güvenlik Filtreleri:**
   - Masal metinlerinde korku, canavar, karanlıkta kaybolma, şiddet, ayrılık anksiyetesi veya cezalandırıcı ögeler asla yer alamaz.
   - Tüm LLM çıktıları pozitif pekiştirme, sevgi, paylaşma, nezaket, problem çözme ve doğa/hayvan sevgisi odaklı olmalıdır.
   - Her kullanıcı girdisi (prompt veya serbest metin) LLM'e gitmeden önce moderasyon katmanından geçirilmelidir.

2. **Sahne ve Yapı Standartları:**
   - Her hikaye tam 4 ila 6 sahneden oluşur (3-6 yaş dikkat süresi: 2-3 dakika).
   - Her sahne 1-2 basit, kafiyeli/akıcı cümleyi aşamaz.
   - LLM her sahne için sahne metni, görsel üretim prompt'u ve ses senkronizasyon verisini JSON formatında üretmelidir.

3. **Görsel Tutarlılık İlkeleri:**
   - Karakter özellikleri (kıyafet, renk, tür) ilk sahnede belirlenir ve sonraki sahnelerde referans prompt olarak aktarılır.
   - İllüstrasyon stili: "Soft watercolor, storybook illustration, whimsical, gentle pastel colors, clean, no text".
   - Görsellerde karmaşık ve korkutucu detaylardan kaçınılmalıdır.

4. **Kelime Senkronizasyonlu Seslendirme (Karaoke Read-Along):**
   - TTS servisi kelime zaman damgası (timestamps) üretmelidir (örn. ElevenLabs).
   - Mobil oynatıcıda ses çalarken aktif kelime parlatılarak görsel-işitsel öğrenme desteklenmelidir.

5. **Ebeveyn Denetimi (Parental Gate):**
   - Ayarlar, masal oluşturma/satın alma butonları ve dış bağlantılar daima rastgele bilişsel/matematiksel bir soru (Parental Gate) arkasında korunmalıdır.

6. **Web Araştırması ve Proje Uygunluk Teyidi (Zorunlu):**
   - Kullanıcıya sunulacak tüm mimari kararlar, kütüphane ve paket önerileri, API entegrasyonları ve kod çözümleri ilk olarak web üzerinden araştırılmalı;
   - Elde edilen güncel çözümler 3-6 yaş pedagojik yapısına, görsel/ses senkronizasyon gereksinimlerine ve performans standartlarına göre teyit edilerek kullanıcıya sunulmalıdır.
