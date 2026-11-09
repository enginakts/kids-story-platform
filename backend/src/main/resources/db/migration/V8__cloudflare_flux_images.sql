-- V8 (Adım 6): Sahne resimleri Cloudflare Workers AI üzerinde FLUX.2 [klein] 4B ile (Apache 2.0 model, günde
-- 10.000 ücretsiz neuron). Model adı yönetimden değiştirilebilir: @cf/black-forest-labs/flux-1-schnell daha ucuzdur
-- ama boyut ve referans görsel almaz. Anahtarlar ortam değişkenlerindedir.
INSERT INTO ai_providers_config (provider_type, provider_name, active_model_name, is_active, priority, timeout_ms) VALUES
 ('IMAGE', 'cloudflare', '@cf/black-forest-labs/flux-2-klein-4b', TRUE, 1, 90000);
