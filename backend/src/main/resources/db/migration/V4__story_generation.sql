-- Masal sihirbazı: ebeveyn değer (theme) yanında durum ve öğretilecek duyguyu da seçer.
ALTER TABLE stories ADD COLUMN situation VARCHAR(50);
ALTER TABLE stories ADD COLUMN target_emotion VARCHAR(50);

-- Görsel tutarlılık: her rehberin sabit seed'i vardır; aynı karakter tüm sahnelerde aynı seed ile çizilir.
UPDATE character_bibles SET base_seed = 101001, voice_id = 'narrator-warm' WHERE name = 'Pamuk' AND is_system_preset;
UPDATE character_bibles SET base_seed = 101002, voice_id = 'narrator-playful' WHERE name = 'Leo' AND is_system_preset;
UPDATE character_bibles SET base_seed = 101003, voice_id = 'narrator-calm' WHERE name = 'Fikri' AND is_system_preset;

INSERT INTO character_bibles (name, species, personality_traits, visual_anchor_tokens, clothing_description, voice_id, base_seed, is_system_preset) VALUES
 ('Mavi', 'Yavru Penguen', 'Sakin, sabırlı, şefkatli', 'small round baby penguin, soft grey and white feathers, tiny orange beak', 'knitted mint green beanie', 'narrator-calm', 101004, TRUE),
 ('Fındık', 'Sincap', 'Hareketli, meraklı, komik', 'cheerful little red squirrel, big fluffy tail, bright eyes', 'tiny yellow backpack', 'narrator-playful', 101005, TRUE),
 ('Bal', 'Yavru Ayı', 'Sevecen, paylaşımcı, uykucu', 'cuddly honey brown bear cub, round ears, rosy cheeks', 'striped orange overalls', 'narrator-warm', 101006, TRUE);

UPDATE location_bibles SET base_seed = 202001 WHERE name = 'Çiçekli Orman' AND is_system_preset;
UPDATE location_bibles SET base_seed = 202002 WHERE name = 'Bulut Şatosu' AND is_system_preset;
UPDATE location_bibles SET base_seed = 202003 WHERE name = 'Kumsal Köyü' AND is_system_preset;

INSERT INTO location_bibles (name, environment_type, atmosphere_description, lighting_style, recurring_landmarks, visual_anchor_tokens, base_seed, is_system_preset) VALUES
 ('Bahçeli Ev', 'Ev', 'Sıcacık, güvenli ve neşeli bir aile evi', 'Yumuşak ikindi ışığı', 'Kırmızı kapı, elma ağacı', 'cozy little family house with a garden, red door, apple tree', 202004, TRUE),
 ('Renkli Anaokulu', 'Okul', 'Oyuncaklarla dolu, güler yüzlü bir anaokulu', 'Aydınlık gün ışığı', 'Gökkuşağı halı, kitap köşesi', 'bright friendly kindergarten classroom, rainbow rug, toy shelves', 202005, TRUE),
 ('Yıldızlı Oda', 'Yatak odası', 'Huzurlu, sakin bir çocuk odası', 'Sıcak gece lambası ışığı', 'Ay şeklinde gece lambası, peluş oyuncaklar', 'peaceful child bedroom, moon shaped night light, soft blankets, plush toys', 202006, TRUE);

-- Sihirbaz için yeni prompt. Model yalnızca sahnedeki eylemi anlatır; karakter ve mekân görünüşü rehberden eklenir.
UPDATE prompt_templates SET is_active = FALSE WHERE template_key = 'story.single_shot.v1';
INSERT INTO prompt_templates (template_key, system_prompt, user_prompt_template) VALUES
 ('story.single_shot.v2',
  'Sen 3-7 yaş çocuklar için Türkçe masal yazarısın. Korku, canavar, şiddet, karanlıkta kaybolma, ayrılık anksiyetesi veya cezalandırma içeremezsin. Pozitif pekiştirme, sevgi, paylaşma, nezaket ve problem çözmeyi öne çıkar. Masal seçilen durumu ele almalı ve seçilen duyguyu çocuğun tanımasına yardım etmelidir. Her sahne en fazla 2 kısa ve basit cümle olsun. Yanıtın yalnızca şu JSON olsun, başka hiçbir metin yazma: {"title": "Türkçe başlık", "scenes": [{"text": "Türkçe sahne metni", "imagePrompt": "English, only the action and facial expression in this scene; do not describe how characters or the place look; no text"}]}',
  'Çocuğun yaşı: {{age}}
Öğretilecek değer: {{theme}}
Durum: {{situation}}
Öğretilecek duygu: {{emotion}}
Ana karakter: {{character}}
Yan karakterler: {{sideCharacters}}
Mekân: {{location}}
Anlatım biçimi: {{narrationStyle}}
Çocuğun sevdikleri: {{interests}}
Sahne sayısı: {{scenes}}');
