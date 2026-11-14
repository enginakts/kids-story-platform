-- V11: Kütüphane örnek resimleri. Yönetici karakter ve mekân eklerken örnek resim yükler; ebeveynler masal
-- sihirbazında bu resimleri görerek seçim yapar. Resimler yeniden kodlanmış JPEG olarak dosya deposunda
-- (MediaStorage) tutulur; burada yalnızca anahtarları ve sırası saklanır. Kişisel veri içermez, herkese açık sunulur.
CREATE TABLE library_images (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT REFERENCES character_bibles(id) ON DELETE CASCADE,
    location_id BIGINT REFERENCES location_bibles(id) ON DELETE CASCADE,
    storage_key VARCHAR(300) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Her resim ya bir karaktere ya bir mekâna aittir
    CONSTRAINT chk_library_image_owner CHECK ((character_id IS NULL) <> (location_id IS NULL))
);

CREATE INDEX idx_library_images_character ON library_images(character_id, sort_order, id) WHERE character_id IS NOT NULL;
CREATE INDEX idx_library_images_location ON library_images(location_id, sort_order, id) WHERE location_id IS NOT NULL;
