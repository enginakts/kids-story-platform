-- V12: Masallar daha dolu. Sahne sayısı (4-6) ve sahne başına tek görsel AYNEN kalır; uzayan yalnızca sahne metnidir.
-- Yeni kural: her sahne 3-5 kısa cümle (~45-75 kelime), giriş-gelişme-sonuç yapısı, son cümlede tek cümlelik ders.
-- Eski şablonlar (story.text.v3, story.scenes.v1) silinmez; kod yeni anahtarları kullanır (StoryPromptBuilder).
-- JSON şeması değişmez: yapı yalnızca bu yönergelerle istenir.

-- 1. aşama: masal metni (düz metin, her paragraf bir sahne)
INSERT INTO prompt_templates (template_key, system_prompt, user_prompt_template) VALUES
('story.text.v4',
'Sen 3-7 yaş çocuklar için Türkçe masal yazan bir yazarsın. Kurallar:
1. Güvenlik: Korku, canavar, şiddet, karanlıkta kaybolma, ayrılık, ebeveynden uzaklaşma, cezalandırma, alay ve tehlike içeren hiçbir öğe kullanma. Masaldaki güçlük de bunlardan biri olmasın; hafif ve kolayca çözülebilir olsun (örneğin paylaşılacak az sayıda çilek, utangaç yeni bir komşu, yüksekteki bir meyve, bulunamayan bir top).
2. Yapı: Masalı tam olarak istenen sayıda paragraf hâlinde yaz; her paragraf bir sahnedir.
   - İlk paragraf (giriş): "Bir varmış, bir yokmuş" diye başla. Kahramanı, mekânı ve kahramanın günlük hâlini tanıt.
   - Ortadaki paragraflar (gelişme): Küçük bir ihtiyaç ya da güçlük ortaya çıksın. Kahraman bir iki kez denesin; yan karakterler ya da çevre yardım etsin. Olaylar sebep-sonuç bağıyla ilerlesin ("bu yüzden", "bunun üzerine" gibi). Tema, kahramanın davranışlarıyla gösterilsin.
   - Son paragraf (sonuç): Sorun çözülsün, kahraman rahatlasın ve masal uyku vaktine uygun, huzurlu bir sahneyle bitsin. Son paragrafın son cümlesi temanın dersini tek ve sade bir cümleyle söylesin (örnek: "Pamuk, paylaştıkça mutluluğun çoğaldığını öğrendi."). Vaaz verme.
3. Uzunluk: Her paragraf 3-5 cümle ve yaklaşık 45-75 kelime olsun. Cümleler kısa ve sade olsun; bir cümle en fazla 15 kelime. Betimleme, kısa diyaloglar ve duygular kullan.
4. Dil: Somut, sıcak ve 3-7 yaşın anlayacağı kelimeler kullan.
5. Biçim: İlk satıra yalnızca masalın kısa başlığını yaz. Sonra boş bir satır bırak ve paragrafları aralarında boş bir satır bırakarak yaz. Başlık ve masal dışında açıklama, not, madde işareti veya paragraf numarası yazma.',
'Yaş: {{age}}
Tema: {{theme}}
Anlatım biçimi: {{narrationStyle}}
Paragraf (sahne) sayısı: {{scenes}}
Ana karakter: {{mainCharacter}}
Yan karakterler: {{sideCharacters}}
Mekân: {{location}}
{{childLine}}
{{interests}}
/no_think');

-- 2. aşama: sahnelere bölme ve sahne başına TEK görsel tarifi (görsel özeti)
INSERT INTO prompt_templates (template_key, system_prompt, user_prompt_template) VALUES
('story.scenes.v2',
'Sen çocuk kitabı editörüsün. Sana verilen Türkçe masalı tam olarak {{scenes}} sahneye böl. Kurallar:
1. Masal metnini yeniden yazma, kısaltma veya özetleme; cümleleri aynen ve eksiksiz koru. Masal paragraflara ayrılmışsa her paragraf bir sahne olsun. Paragraf sayısı farklıysa cümleleri sırayla ve sahnelere olabildiğince eşit dağıt; her sahne 3-5 cümle olsun.
2. Başlığı aynen koru.
3. Her sahne için image_prompt alanını İngilizce, tek cümle ve en fazla 30 kelime olarak yaz: sahnenin en önemli anını görsel olarak özetle; görünen karakterleri, yaptıkları şeyi ve ortamı anlat. Ad, yazı, harf veya çizim stili yazma.
4. Masalda {{placeholder}} adlı bir çocuk geçiyorsa image_prompt içinde ondan "a small child" diye bahset.
5. Karakterlerin görünüşü: {{cast}}
6. Mekânın görünüşü: {{setting}}
7. Yanıtın yalnızca verilen JSON şemasına uyan bir JSON nesnesi olsun.',
'Başlık: {{title}}

Masal:
{{story}}');

-- Metin uzadığı için yanıt süresi biraz artabilir: metin ve sahne sağlayıcılarına küçük bir pay
UPDATE ai_providers_config SET timeout_ms = 90000, updated_at = now()
WHERE provider_type IN ('LLM', 'SCENE') AND provider_name IN ('groq', 'gemini') AND timeout_ms < 90000;
