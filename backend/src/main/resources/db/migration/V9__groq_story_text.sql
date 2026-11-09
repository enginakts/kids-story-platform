-- V9: Proje sahibinin kararıyla masal metni Groq üzerinden (openai/gpt-oss-120b) yazılır.
-- Featherless (Trendyol LLM) silinmez; 2. sıraya iner ve anahtarı verilirse Groq hata verdiğinde yedek olur.
-- Sahnelere bölme Gemini'de kalır. Aynı metin prompt'u (story.text.v3) kullanılır.
UPDATE ai_providers_config SET is_active = TRUE, priority = 1, timeout_ms = 60000, updated_at = now()
WHERE provider_type = 'LLM' AND provider_name = 'groq';
UPDATE ai_providers_config SET priority = 2, updated_at = now()
WHERE provider_type = 'LLM' AND provider_name = 'featherless';
