-- V15: Ebeveyn hesabını silince YAYIMLANMIŞ masallar (published_at dolu: onaylı, herkese açık, tamamlanmış) Keşfet için korunur.
-- Bu masallar sahipsiz kalır: ebeveyn ve çocuk bağlantısı boşa düşer (SET NULL). Yayımlanmamış masallar ise hesap
-- silinirken kodla açıkça silinir (ProfileService). Ebeveynin kendi karakteri silinince masalın karakter bağlantısı da düşer;
-- sahne resimleri zaten çizilmiş olduğundan masal okunmaya devam eder. Çocuğun adı ve seslendirme kodla anonimleştirilir.

ALTER TABLE stories ALTER COLUMN parent_id DROP NOT NULL;
ALTER TABLE stories ALTER COLUMN child_id DROP NOT NULL;

ALTER TABLE stories DROP CONSTRAINT stories_parent_id_fkey;
ALTER TABLE stories ADD CONSTRAINT stories_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES parents(id) ON DELETE SET NULL;

ALTER TABLE stories DROP CONSTRAINT stories_child_id_fkey;
ALTER TABLE stories ADD CONSTRAINT stories_child_id_fkey FOREIGN KEY (child_id) REFERENCES child_profiles(id) ON DELETE SET NULL;

ALTER TABLE stories DROP CONSTRAINT stories_primary_character_id_fkey;
ALTER TABLE stories ADD CONSTRAINT stories_primary_character_id_fkey
    FOREIGN KEY (primary_character_id) REFERENCES character_bibles(id) ON DELETE SET NULL;

-- story_characters.character_id zorunlu: karakter silinince yalnızca bağlantı satırı silinir
ALTER TABLE story_characters DROP CONSTRAINT story_characters_character_id_fkey;
ALTER TABLE story_characters ADD CONSTRAINT story_characters_character_id_fkey
    FOREIGN KEY (character_id) REFERENCES character_bibles(id) ON DELETE CASCADE;

-- Sahipsiz kalan bir masal yalnızca yayımlanmış olabilir (yayımlanmamış masal sahipsiz kalamaz)
ALTER TABLE stories ADD CONSTRAINT chk_stories_owner_or_published CHECK (
    (parent_id IS NOT NULL AND child_id IS NOT NULL) OR published_at IS NOT NULL
);
