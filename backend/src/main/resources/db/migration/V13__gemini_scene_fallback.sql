-- V13: Sahnelere bölmede yedek sağlayıcı. gemini-3.6-flash yoğunken (HTTP 503 "high demand") masal düşmesin diye
-- aynı Gemini anahtarıyla daha hafif bir model 2. önceliğe girer; yönlendirici (AiRouter) ana model hata verince aynı denemede ona geçer.
-- Taslak (stub-scene) 100. öncelikte kalır; yalnızca anahtar yokken kullanılır. Yönetimden kapatılabilir veya model değiştirilebilir.
INSERT INTO ai_providers_config (provider_type, provider_name, active_model_name, is_active, priority, timeout_ms) VALUES
 ('SCENE', 'gemini-lite', 'gemini-3.5-flash-lite', TRUE, 2, 90000)
ON CONFLICT (provider_type, provider_name) DO NOTHING;
