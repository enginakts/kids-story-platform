-- V1: Vize kapsamı çekirdek şema. Kaynak: platform_mimari_ve_veritabani_rehberi.md
-- Rehberdeki 20 tablo + proje tanımının gerektirdiği 7 ek tablo. Yaş aralığı 3-7.

-- ===== Kimlik =====
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admins (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role_id BIGINT NOT NULL REFERENCES roles(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE parents (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(30),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    preferred_language VARCHAR(10) NOT NULL DEFAULT 'tr',
    avatar_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- EK: e-posta doğrulama ve parola sıfırlama (yalnızca hash saklanır)
CREATE TABLE account_tokens (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    token_type VARCHAR(30) NOT NULL,
    token_hash VARCHAR(64) UNIQUE NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- EK: mobil uygulamada kalıcı oturum (yalnızca hash saklanır)
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) UNIQUE NOT NULL,
    device_name VARCHAR(100),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- EK: ticari ürün için KVKK / ebeveyn onayı kanıtı
CREATE TABLE parent_consents (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    consent_type VARCHAR(50) NOT NULL,
    document_version VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===== Çocuk profili =====
-- DEĞİŞİKLİK: avatar_url yerine avatar_key. Çocuk verisi dış avatar servislerine gönderilmez.
CREATE TABLE child_profiles (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    age INT NOT NULL CHECK (age BETWEEN 3 AND 7),
    gender VARCHAR(20),
    favorite_animals TEXT,
    favorite_colors TEXT,
    special_interests TEXT,
    bedtime_hour TIME NOT NULL DEFAULT '20:30:00',
    avatar_key VARCHAR(50) NOT NULL DEFAULT 'default',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===== Tutarlılık rehberleri =====
CREATE TABLE character_bibles (
    id BIGSERIAL PRIMARY KEY,
    child_id BIGINT REFERENCES child_profiles(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    species VARCHAR(50) NOT NULL,
    personality_traits TEXT,
    visual_anchor_tokens TEXT NOT NULL,
    clothing_description TEXT,
    voice_id VARCHAR(100),
    base_seed BIGINT,
    reference_image_url VARCHAR(500),
    is_system_preset BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE location_bibles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    environment_type VARCHAR(100) NOT NULL,
    atmosphere_description TEXT NOT NULL,
    lighting_style VARCHAR(100) NOT NULL,
    recurring_landmarks TEXT,
    visual_anchor_tokens TEXT NOT NULL,
    reference_image_url VARCHAR(500),
    base_seed BIGINT,
    is_system_preset BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===== Masal =====
-- DEĞİŞİKLİK: narration_style, target_duration_minutes, visibility, moderation_status, published_at eklendi;
-- is_favorite kaldırıldı (bkz. story_favorites).
CREATE TABLE stories (
    id BIGSERIAL PRIMARY KEY,
    child_id BIGINT NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
    parent_id BIGINT NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    primary_character_id BIGINT REFERENCES character_bibles(id),
    primary_location_id BIGINT REFERENCES location_bibles(id),
    title VARCHAR(255) NOT NULL,
    theme VARCHAR(50) NOT NULL,
    narration_style VARCHAR(50) NOT NULL DEFAULT 'WARM',
    target_duration_minutes INT NOT NULL DEFAULT 3 CHECK (target_duration_minutes BETWEEN 1 AND 10),
    story_mode VARCHAR(50) NOT NULL DEFAULT 'SINGLE_SHOT',
    personalized_used BOOLEAN NOT NULL DEFAULT FALSE,
    total_scenes INT NOT NULL DEFAULT 4 CHECK (total_scenes BETWEEN 4 AND 6),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    moderation_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    published_at TIMESTAMPTZ,
    cover_image_url VARCHAR(500),
    storage_expiry_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Onaysız masal veritabanı düzeyinde de yayımlanamaz.
    CONSTRAINT chk_stories_published_approved CHECK (
        published_at IS NULL OR (visibility = 'PUBLIC' AND moderation_status = 'APPROVED' AND status = 'COMPLETED')
    )
);

-- EK: yan karakterler (rehberde Faz 1'den çıkarılmıştı, proje tanımı gerektiriyor)
CREATE TABLE story_characters (
    id BIGSERIAL PRIMARY KEY,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    character_id BIGINT NOT NULL REFERENCES character_bibles(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (story_id, character_id)
);

-- EK: keşfetteki herkese açık masallar başka ebeveynlerce de favorilenebilir
CREATE TABLE story_favorites (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (parent_id, story_id)
);

CREATE TABLE story_scenes (
    id BIGSERIAL PRIMARY KEY,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    location_id BIGINT REFERENCES location_bibles(id),
    scene_number INT NOT NULL,
    scene_text TEXT NOT NULL,
    voice_text TEXT NOT NULL,
    image_prompt TEXT NOT NULL,
    negative_prompt TEXT NOT NULL DEFAULT 'no text, no watermark, no dark colors, no horror',
    image_url VARCHAR(500),
    audio_url VARCHAR(500),
    audio_duration_seconds DECIMAL(6, 2),
    timestamps_json JSONB,
    generation_seed BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (story_id, scene_number)
);

CREATE TABLE interactive_nodes (
    id BIGSERIAL PRIMARY KEY,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    scene_id BIGINT REFERENCES story_scenes(id) ON DELETE SET NULL,
    node_step INT NOT NULL,
    prompt_question VARCHAR(255) NOT NULL,
    choice_a_text VARCHAR(100) NOT NULL,
    choice_a_icon VARCHAR(100) NOT NULL,
    choice_b_text VARCHAR(100) NOT NULL,
    choice_b_icon VARCHAR(100) NOT NULL,
    selected_choice VARCHAR(10) CHECK (selected_choice IN ('A', 'B')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (story_id, node_step)
);

CREATE TABLE child_reading_sessions (
    id BIGSERIAL PRIMARY KEY,
    child_id BIGINT NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    duration_seconds INT NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    session_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- EK: asenkron üretim kuyruğu (ücretsiz katmanda Redis yerine veritabanı kuyruğu)
CREATE TABLE generation_jobs (
    id BIGSERIAL PRIMARY KEY,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    scene_id BIGINT REFERENCES story_scenes(id) ON DELETE CASCADE,
    job_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    provider_name VARCHAR(100),
    last_error VARCHAR(500),
    run_after TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- EK: herkese açık masalların denetim geçmişi
CREATE TABLE story_moderation_reviews (
    id BIGSERIAL PRIMARY KEY,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    admin_id BIGINT REFERENCES admins(id),
    decision VARCHAR(20) NOT NULL,
    review_source VARCHAR(20) NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===== Abonelik ve kredi =====
CREATE TABLE subscription_plans (
    id BIGSERIAL PRIMARY KEY,
    plan_code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    monthly_story_credits INT NOT NULL,
    interactive_credits INT NOT NULL,
    max_child_profiles INT NOT NULL DEFAULT 1,
    storage_retention_days INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- DEĞİŞİKLİK: end_date boş olabilir (süresiz ücretsiz plan)
CREATE TABLE parent_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES subscription_plans(id),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    start_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_date TIMESTAMPTZ,
    auto_renew BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- DEĞİŞİKLİK: version (eşzamanlı çift harcamaya karşı iyimser kilit) ve negatif bakiye engeli
CREATE TABLE credit_wallets (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT UNIQUE NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    story_credit_balance INT NOT NULL DEFAULT 3 CHECK (story_credit_balance >= 0),
    interactive_credit_balance INT NOT NULL DEFAULT 1 CHECK (interactive_credit_balance >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- DEĞİŞİKLİK: story_id (harcama ve iadeyi masala bağlar)
CREATE TABLE credit_transactions (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL REFERENCES credit_wallets(id) ON DELETE CASCADE,
    story_id BIGINT REFERENCES stories(id) ON DELETE SET NULL,
    transaction_type VARCHAR(50) NOT NULL,
    story_credit_amount INT NOT NULL,
    interactive_credit_amount INT NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===== Sistem, yapay zekâ ve güvenlik =====
CREATE TABLE system_settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) UNIQUE NOT NULL,
    setting_value TEXT NOT NULL,
    data_type VARCHAR(30) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- API anahtarları burada tutulmaz; yalnızca ortam değişkenlerinden okunur.
CREATE TABLE ai_providers_config (
    id BIGSERIAL PRIMARY KEY,
    provider_type VARCHAR(50) NOT NULL,
    provider_name VARCHAR(100) NOT NULL,
    active_model_name VARCHAR(150) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT NOT NULL DEFAULT 1,
    timeout_ms INT NOT NULL DEFAULT 15000,
    cost_per_1k_tokens DECIMAL(8, 6) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider_type, provider_name)
);

CREATE TABLE prompt_templates (
    id BIGSERIAL PRIMARY KEY,
    template_key VARCHAR(100) UNIQUE NOT NULL,
    system_prompt TEXT NOT NULL,
    user_prompt_template TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE content_blacklists (
    id BIGSERIAL PRIMARY KEY,
    keyword VARCHAR(100) UNIQUE NOT NULL,
    category VARCHAR(50) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    replacement_word VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT NOT NULL REFERENCES admins(id),
    action VARCHAR(100) NOT NULL,
    target_entity VARCHAR(100) NOT NULL,
    target_id BIGINT,
    details TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE parental_gate_attempts (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    challenge_question VARCHAR(100) NOT NULL,
    is_success BOOLEAN NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===== İndeksler (UNIQUE sütunlar zaten indekslidir) =====
CREATE INDEX idx_account_tokens_parent ON account_tokens(parent_id);
CREATE INDEX idx_refresh_tokens_parent ON refresh_tokens(parent_id);
CREATE INDEX idx_child_profiles_parent ON child_profiles(parent_id);
CREATE INDEX idx_character_bibles_child ON character_bibles(child_id);
CREATE INDEX idx_stories_child ON stories(child_id);
CREATE INDEX idx_stories_parent ON stories(parent_id);
CREATE INDEX idx_stories_explore ON stories(published_at DESC) WHERE published_at IS NOT NULL;
CREATE INDEX idx_stories_moderation_queue ON stories(created_at) WHERE moderation_status = 'PENDING';
CREATE INDEX idx_generation_jobs_queue ON generation_jobs(run_after) WHERE status = 'QUEUED';
CREATE INDEX idx_generation_jobs_story ON generation_jobs(story_id);
CREATE INDEX idx_moderation_reviews_story ON story_moderation_reviews(story_id);
CREATE INDEX idx_reading_sessions_child ON child_reading_sessions(child_id);
CREATE INDEX idx_credit_transactions_wallet ON credit_transactions(wallet_id);
