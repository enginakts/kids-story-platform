-- V20: Seslendirmeye Murf AI eklendi (MURF_API_KEY). Sıra: 1 Gemini TTS, 2 Murf, 3 Azure.
-- Murf'te yerli Türkçe ses yoktur; çok dilli sesler locale=tr-TR ile Türkçe konuşur. Model adı ses kimliğidir:
-- en-US-natalie (kadın, anlatıcı) varsayılan; en-UK-juliet, en-US-amara (kadın), en-US-ken (erkek) de Türkçe konuşur.
UPDATE ai_providers_config SET priority = 3, updated_at = now() WHERE provider_type = 'TTS' AND provider_name = 'azure';
INSERT INTO ai_providers_config (provider_type, provider_name, active_model_name, is_active, priority, timeout_ms) VALUES
 ('TTS', 'murf', 'en-US-natalie', TRUE, 2, 90000)
ON CONFLICT (provider_type, provider_name) DO NOTHING;
