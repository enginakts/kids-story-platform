-- V2: Uygulamanın çalışması için gereken başlangıç verileri. Hesap veya parola içermez.
INSERT INTO roles (name, display_name, description) VALUES
 ('ROLE_PARENT', 'Ebeveyn', 'Çocuk profili yöneten ve masal üreten hesap'),
 ('ROLE_ADMIN', 'Yönetici', 'Kullanıcı ve masal denetimi yapan personel'),
 ('ROLE_SUPER_ADMIN', 'Süper Yönetici', 'Sistem ayarlarını yöneten personel');

-- Ücretli planların fiyatı belirlenene kadar kapalıdır.
INSERT INTO subscription_plans (plan_code, name, price, monthly_story_credits, interactive_credits, max_child_profiles, storage_retention_days, is_active) VALUES
 ('FREE_TIER', 'Ücretsiz', 0, 3, 1, 2, 7, TRUE),
 ('STANDARD_MONTHLY', 'Standart', 0, 15, 5, 3, 180, FALSE),
 ('FAMILY_VIP', 'Aile VIP', 0, 40, 15, 6, 3650, FALSE);

INSERT INTO character_bibles (name, species, personality_traits, visual_anchor_tokens, clothing_description, is_system_preset) VALUES
 ('Pamuk', 'Tavşan', 'Meraklı, nazik, yardımsever', 'small fluffy white bunny, big round eyes', 'pastel blue scarf', TRUE),
 ('Leo', 'Yavru Aslan', 'Cesur, neşeli, paylaşımcı', 'cute lion cub, soft golden mane', 'little red bow tie', TRUE),
 ('Fikri', 'Yavru Fil', 'Düşünceli, sakin, sevecen', 'small pastel blue baby elephant', 'yellow daisy crown', TRUE);

INSERT INTO location_bibles (name, environment_type, atmosphere_description, lighting_style, visual_anchor_tokens, is_system_preset) VALUES
 ('Çiçekli Orman', 'Orman', 'Güneşli, huzurlu ve renkli bir orman', 'Yumuşak gün ışığı', 'sunny whimsical forest, colorful flowers', TRUE),
 ('Bulut Şatosu', 'Gökyüzü', 'Pamuk bulutların üzerinde sevimli bir şato', 'Pastel gün batımı', 'cute castle on fluffy clouds, pastel sky', TRUE),
 ('Kumsal Köyü', 'Deniz kıyısı', 'Küçük renkli evlerin olduğu sakin bir kıyı', 'Parlak sabah ışığı', 'gentle beach village, tiny colorful houses', TRUE);

INSERT INTO system_settings (setting_key, setting_value, data_type, category, description) VALUES
 ('story.min_scenes', '4', 'INTEGER', 'STORY', 'Masal başına en az sahne'),
 ('story.max_scenes', '6', 'INTEGER', 'STORY', 'Masal başına en fazla sahne'),
 ('child.min_age', '3', 'INTEGER', 'CHILD', 'En küçük çocuk yaşı'),
 ('child.max_age', '7', 'INTEGER', 'CHILD', 'En büyük çocuk yaşı');

-- Gerçek sağlayıcılar araştırılıp eklenene kadar yalnızca geliştirme taslakları tanımlıdır.
INSERT INTO ai_providers_config (provider_type, provider_name, active_model_name, is_active, priority) VALUES
 ('LLM', 'stub', 'stub', TRUE, 100),
 ('IMAGE', 'stub', 'stub', TRUE, 100),
 ('TTS', 'stub', 'stub', TRUE, 100);

INSERT INTO prompt_templates (template_key, system_prompt, user_prompt_template) VALUES
 ('story.single_shot.v1',
  'Sen 3-7 yaş çocuklar için Türkçe masal yazarısın. Korku, canavar, şiddet, karanlıkta kaybolma, ayrılık anksiyetesi veya cezalandırma içeremezsin. Pozitif pekiştirme, sevgi, paylaşma, nezaket ve problem çözmeyi öne çıkar. Her sahne en fazla 2 basit cümle olsun. Yanıtın yalnızca istenen JSON şemasına uysun.',
  'Yaş: {{age}}. Tema: {{theme}}. Ana karakter: {{character}}. Yan karakterler: {{sideCharacters}}. Mekân: {{location}}. Anlatım: {{narrationStyle}}. Süre: yaklaşık {{minutes}} dakika. Sahne sayısı: {{scenes}}.');

INSERT INTO content_blacklists (keyword, category, action_type, replacement_word) VALUES
 ('canavar', 'FEAR', 'REPLACE', 'sevimli dost'),
 ('korkunç', 'FEAR', 'REPLACE', 'şaşırtıcı'),
 ('kan', 'VIOLENCE', 'BLOCK', NULL),
 ('öldür', 'VIOLENCE', 'BLOCK', NULL),
 ('terk', 'SEPARATION', 'BLOCK', NULL);
