-- V4 (Adım 4): Karakterler çocuğa değil ebeveyne aittir; kardeşler aynı karakteri kullanabilir.
-- Masallar yine tek çocuğa bağlıdır (stories.child_id), veri izolasyonu oradan korunur.

ALTER TABLE character_bibles ADD COLUMN parent_id BIGINT REFERENCES parents(id) ON DELETE CASCADE;

UPDATE character_bibles cb
SET parent_id = cp.parent_id
FROM child_profiles cp
WHERE cb.child_id = cp.id;

DROP INDEX IF EXISTS idx_character_bibles_child;
ALTER TABLE character_bibles DROP COLUMN child_id;

-- Ebeveynin listelerden yaptığı seçimler; düzenleme formu bunlardan doldurulur.
-- Sistem şablonlarında boştur. visual_anchor_tokens bu seçimlerden sistemce üretilir.
ALTER TABLE character_bibles ADD COLUMN species_code VARCHAR(30);
ALTER TABLE character_bibles ADD COLUMN color_code VARCHAR(30);
ALTER TABLE character_bibles ADD COLUMN outfit_code VARCHAR(30);
ALTER TABLE character_bibles ADD COLUMN accessory_code VARCHAR(30);

-- Masalda kullanılmış karakter silinemez; silme pasife almadır.
ALTER TABLE character_bibles ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Sistem şablonunun sahibi yoktur, ebeveyn karakterinin sahibi vardır.
ALTER TABLE character_bibles ADD CONSTRAINT chk_character_owner
    CHECK (is_system_preset = (parent_id IS NULL));

CREATE INDEX idx_character_bibles_parent ON character_bibles(parent_id) WHERE is_active;

INSERT INTO system_settings (setting_key, setting_value, data_type, category, description) VALUES
 ('character.max_per_parent', '20', 'INTEGER', 'CHARACTER', 'Ebeveyn başına en fazla aktif karakter');
