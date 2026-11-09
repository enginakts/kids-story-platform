-- V7 (Adım 7): Seslendirme Kikiri Türkçe TTS ile (duxx/kikiri-turkish-base, Kokoro-82M tabanlı, ücretsiz).
-- Model kendi çalıştırdığımız tts-service üzerinden kullanılır. Masal sesi CPU'da yaklaşık gerçek zamanlı
-- üretildiği için zaman aşımı uzun tutulur; ücretsiz Hugging Face Space uykudan uyanırken de yeterli olur.
INSERT INTO ai_providers_config (provider_type, provider_name, active_model_name, is_active, priority, timeout_ms) VALUES
 ('TTS', 'kikiri', 'duxx/kikiri-turkish-base (nisan)', TRUE, 1, 180000);

