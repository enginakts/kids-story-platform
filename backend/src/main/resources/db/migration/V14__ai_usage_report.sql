-- V14: Yapay zekâ kullanım ve maliyet raporu (yalnızca süper yönetici, /admin/usage).
-- Her dış sağlayıcı çağrısı (deneme ve yedek dahil) bir satır olur; kayıt mevcut çağrının içinde tutulur, ek çağrı yapılmaz.
-- Gizlilik: ebeveyn, çocuk adı veya metin tutulmaz; yalnızca masal ve iş kimliği (masal silinince boşa düşer).
CREATE TABLE ai_usage_events (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    provider_type VARCHAR(20) NOT NULL,
    provider_name VARCHAR(100) NOT NULL,
    model_name VARCHAR(150) NOT NULL,
    story_id BIGINT REFERENCES stories(id) ON DELETE SET NULL,
    job_id BIGINT,
    is_success BOOLEAN NOT NULL,
    http_status INT,
    error_message VARCHAR(300),
    duration_ms INT NOT NULL,
    input_tokens INT,
    output_tokens INT,
    estimated_cost DECIMAL(12, 6) NOT NULL DEFAULT 0
);
CREATE INDEX idx_ai_usage_created ON ai_usage_events(created_at);
CREATE INDEX idx_ai_usage_provider_created ON ai_usage_events(provider_type, provider_name, created_at);

-- Rapordaki "kalan" ve maliyet hesabı için yönetimden girilen değerler (boş limit = sınırsız / bilinmiyor).
-- Tahmini maliyet = çağrı başı ücret + (giriş + çıkış token) / 1000 × cost_per_1k_tokens.
ALTER TABLE ai_providers_config ADD COLUMN daily_limit INT CHECK (daily_limit IS NULL OR daily_limit > 0);
ALTER TABLE ai_providers_config ADD COLUMN monthly_limit INT CHECK (monthly_limit IS NULL OR monthly_limit > 0);
ALTER TABLE ai_providers_config ADD COLUMN cost_per_call DECIMAL(10, 6) NOT NULL DEFAULT 0 CHECK (cost_per_call >= 0);
