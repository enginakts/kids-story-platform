-- V19: Seslendirme bulut servislerine taşındı; Python ile çalışan Kikiri servisi (tts-service) kaldırıldı.
-- 1. sıra Gemini TTS (sahnelere bölmeyle aynı GEMINI_API_KEY, ücretsiz kota). Kota dolunca (HTTP 429) yönlendirici
-- aynı denemede 2. sıraya, Azure AI Speech'e (ücretsiz F0: ayda 0,5 milyon karakter) geçer. Azure satırındaki model adı
-- kullanılacak Türkçe sestir (tr-TR-EmelNeural kadın, tr-TR-AhmetNeural erkek). Sıra ve modeller yönetimden değiştirilebilir.
-- Eski kayıtlarda geçen 'kikiri' adı (kullanım raporu, üretim işleri) metin olarak kalır; yalnızca sağlayıcı satırı silinir.
DELETE FROM ai_providers_config WHERE provider_type = 'TTS' AND provider_name = 'kikiri';
INSERT INTO ai_providers_config (provider_type, provider_name, active_model_name, is_active, priority, timeout_ms) VALUES
 ('TTS', 'gemini-tts', 'gemini-3.8-flash-lite-tts', TRUE, 1, 120000),
 ('TTS', 'azure', 'tr-TR-EmelNeural', TRUE, 2, 60000)
ON CONFLICT (provider_type, provider_name) DO NOTHING;
