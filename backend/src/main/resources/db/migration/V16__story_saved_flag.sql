-- V16: Masal üretilince otomatik kaydedilmez. saved_at boşsa masal "geçici"dir: çocuğun listesinde görünmez,
-- ebeveyn "Bu masalı kaydet" deyince kaydedilir. Kaydedilmeyen masal app.stories.unsaved-ttl-hours (varsayılan 24) sonra
-- dosyalarıyla birlikte temizlenir (UnsavedStoryCleaner). Mevcut masalların hepsi kaydedilmiş sayılır.
ALTER TABLE stories ADD COLUMN saved_at TIMESTAMPTZ;
UPDATE stories SET saved_at = created_at;
CREATE INDEX idx_stories_unsaved ON stories(created_at) WHERE saved_at IS NULL;

-- Yayımlanmış bir masal kaydedilmiş olmalıdır (geçici masal Keşfet'e çıkamaz)
ALTER TABLE stories ADD CONSTRAINT chk_stories_published_saved CHECK (published_at IS NULL OR saved_at IS NOT NULL);
