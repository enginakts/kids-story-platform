-- V10: Karakter ve mekânları yalnızca yöneticiler tanımlar (yönetim paneli > Karakterler / Mekânlar).
-- Tanımlananlar bütün ebeveynlere görünür; ebeveynler değiştiremez, yalnızca masal oluştururken seçer.
-- Ebeveynlerin daha önce oluşturduğu karakterler silinmez (eski masallar onlara bağlıdır) ama artık seçilemez.

-- Mekân da karakter gibi yayından kaldırılabilir; masallarda kullanılmış olabileceği için silinmez.
ALTER TABLE location_bibles ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Mekânın ışığı ve sabit öğeleri her sahne resminin isteğine eklenir; resim modeli İngilizce çalıştığı için
-- bu iki alan İngilizcedir. Hazır üç mekânın Türkçe ışık değerleri İngilizceye çevrilir.
UPDATE location_bibles SET lighting_style = 'soft warm morning sunlight through the leaves',
    recurring_landmarks = 'a big friendly oak tree, a meadow of wildflowers, a small wooden bridge over a stream'
WHERE name = 'Çiçekli Orman' AND is_system_preset;
UPDATE location_bibles SET lighting_style = 'pastel pink and lilac sunset glow',
    recurring_landmarks = 'a round castle with pastel towers, fluffy cloud stairs, a rainbow bridge'
WHERE name = 'Bulut Şatosu' AND is_system_preset;
UPDATE location_bibles SET lighting_style = 'bright clear morning light by the sea',
    recurring_landmarks = 'tiny colorful houses, a small lighthouse, sailboats in a calm bay'
WHERE name = 'Kumsal Köyü' AND is_system_preset;

-- Hazır karakterlere yönetim formunun kullandığı katalog kodları (görünüm tarifleri değişmez;
-- yönetici karakteri kaydederse tarif bu kodlardan yeniden üretilir).
UPDATE character_bibles SET species_code = 'TAVSAN', color_code = 'BEYAZ', outfit_code = 'ATKI', accessory_code = 'YOK'
WHERE name = 'Pamuk' AND is_system_preset;
UPDATE character_bibles SET species_code = 'ASLAN', color_code = 'SARI', outfit_code = 'YOK', accessory_code = 'PAPYON'
WHERE name = 'Leo' AND is_system_preset;
UPDATE character_bibles SET species_code = 'FIL', color_code = 'MAVI', outfit_code = 'YOK', accessory_code = 'CICEK_TACI'
WHERE name = 'Fikri' AND is_system_preset;

-- Ebeveyn başına karakter sınırı artık anlamsız.
DELETE FROM system_settings WHERE setting_key = 'character.max_per_parent';
