-- V21: Seslendirme sağlayıcısı masal başına seçilir (TtsPlanner). Masalın bütün sahneleri aynı sağlayıcıyla (aynı sesle)
-- okunur; ardışık masallar kullanım ve kalan kota durumuna göre farklı sağlayıcılara dağıtılır.
ALTER TABLE stories ADD COLUMN tts_provider VARCHAR(100);
