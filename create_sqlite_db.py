import sqlite3
import os

db_path = os.path.join(r"c:\Users\engin\Desktop\kids-histories\backend", "kidstory.db")

# Delete if exists to ensure clean initial schema
if os.path.exists(db_path):
    os.remove(db_path)

conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# Enable Foreign Keys in SQLite
cursor.execute("PRAGMA foreign_keys = ON;")

ddl_script = """
-- 1. ROLLER (roles)
CREATE TABLE IF NOT EXISTS roles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(50) UNIQUE NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. YÖNETİCİLER (admins)
CREATE TABLE IF NOT EXISTS admins (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role_id INTEGER NOT NULL REFERENCES roles(id),
    is_active BOOLEAN NOT NULL DEFAULT 1,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. EBEVEYNLER (parents)
CREATE TABLE IF NOT EXISTS parents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(30),
    role_id INTEGER NOT NULL REFERENCES roles(id),
    preferred_language VARCHAR(10) DEFAULT 'tr',
    avatar_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT 1,
    is_email_verified BOOLEAN NOT NULL DEFAULT 0,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. ÇOCUK PROFİLLERİ (child_profiles)
CREATE TABLE IF NOT EXISTS child_profiles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    parent_id INTEGER NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    age INTEGER NOT NULL CHECK (age >= 2 AND age <= 8),
    gender VARCHAR(20),
    favorite_animals TEXT,
    favorite_colors TEXT,
    special_interests TEXT,
    bedtime_hour VARCHAR(10) DEFAULT '20:30:00',
    avatar_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. ÇOCUK OKUMA SEANSLARI (child_reading_sessions)
CREATE TABLE IF NOT EXISTS child_reading_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    child_id INTEGER NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
    story_id INTEGER NOT NULL,
    duration_seconds INTEGER NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT 0,
    session_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. KARAKTER İNCİLİ (character_bibles)
CREATE TABLE IF NOT EXISTS character_bibles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    child_id INTEGER REFERENCES child_profiles(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    species VARCHAR(50) NOT NULL,
    personality_traits TEXT,
    visual_anchor_tokens TEXT NOT NULL,
    clothing_description TEXT,
    voice_id VARCHAR(100),
    base_seed INTEGER,
    reference_image_url VARCHAR(500),
    is_system_preset BOOLEAN NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 7. MEKÂN İNCİLİ (location_bibles)
CREATE TABLE IF NOT EXISTS location_bibles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(150) NOT NULL,
    environment_type VARCHAR(100) NOT NULL,
    atmosphere_description TEXT NOT NULL,
    lighting_style VARCHAR(100) NOT NULL,
    recurring_landmarks TEXT,
    visual_anchor_tokens TEXT NOT NULL,
    reference_image_url VARCHAR(500),
    base_seed INTEGER,
    is_system_preset BOOLEAN NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 8. MASALLAR (stories)
CREATE TABLE IF NOT EXISTS stories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    child_id INTEGER NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
    parent_id INTEGER NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    primary_character_id INTEGER REFERENCES character_bibles(id),
    primary_location_id INTEGER REFERENCES location_bibles(id),
    title VARCHAR(255) NOT NULL,
    theme VARCHAR(150) NOT NULL,
    story_mode VARCHAR(50) NOT NULL DEFAULT 'SINGLE_SHOT',
    personalized_used BOOLEAN NOT NULL DEFAULT 0,
    total_scenes INTEGER NOT NULL DEFAULT 4,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    cover_image_url VARCHAR(500),
    storage_expiry_date TIMESTAMP,
    is_favorite BOOLEAN NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 9. MASAL SAHNELERİ (story_scenes)
CREATE TABLE IF NOT EXISTS story_scenes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    story_id INTEGER NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    location_id INTEGER REFERENCES location_bibles(id),
    scene_number INTEGER NOT NULL,
    scene_text TEXT NOT NULL,
    voice_text TEXT NOT NULL,
    image_prompt TEXT NOT NULL,
    negative_prompt TEXT DEFAULT 'no text, no watermark, no dark colors, no horror',
    image_url VARCHAR(500),
    audio_url VARCHAR(500),
    audio_duration_seconds DECIMAL(6, 2),
    timestamps_json TEXT,
    generation_seed INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (story_id, scene_number)
);

-- 10. ETKİLEŞİMLİ DÜĞÜMLER (interactive_nodes)
CREATE TABLE IF NOT EXISTS interactive_nodes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    story_id INTEGER NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    scene_id INTEGER REFERENCES story_scenes(id) ON DELETE SET NULL,
    node_step INTEGER NOT NULL,
    prompt_question VARCHAR(255) NOT NULL,
    choice_a_text VARCHAR(100) NOT NULL,
    choice_a_icon VARCHAR(100) NOT NULL,
    choice_b_text VARCHAR(100) NOT NULL,
    choice_b_icon VARCHAR(100) NOT NULL,
    selected_choice VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 11. ABONELİK PLANLARI (subscription_plans)
CREATE TABLE IF NOT EXISTS subscription_plans (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    monthly_story_credits INTEGER NOT NULL,
    interactive_credits INTEGER NOT NULL,
    max_child_profiles INTEGER NOT NULL DEFAULT 1,
    storage_retention_days INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT 1
);

-- 12. EBEVEYN ABONELİKLERİ (parent_subscriptions)
CREATE TABLE IF NOT EXISTS parent_subscriptions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    parent_id INTEGER NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    plan_id INTEGER NOT NULL REFERENCES subscription_plans(id),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    start_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_date TIMESTAMP NOT NULL,
    auto_renew BOOLEAN NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 13. KREDİ CÜZDANI (credit_wallets)
CREATE TABLE IF NOT EXISTS credit_wallets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    parent_id INTEGER UNIQUE NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    story_credit_balance INTEGER NOT NULL DEFAULT 3,
    interactive_credit_balance INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 14. KREDİ HAREKETLERİ (credit_transactions)
CREATE TABLE IF NOT EXISTS credit_transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    wallet_id INTEGER NOT NULL REFERENCES credit_wallets(id) ON DELETE CASCADE,
    transaction_type VARCHAR(50) NOT NULL,
    story_credit_amount INTEGER NOT NULL,
    interactive_credit_amount INTEGER NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 15. DİNAMİK SİSTEM AYARLARI (system_settings)
CREATE TABLE IF NOT EXISTS system_settings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    setting_key VARCHAR(100) UNIQUE NOT NULL,
    setting_value TEXT NOT NULL,
    data_type VARCHAR(30) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 16. YAPAY ZEKÂ SAĞLAYICILARI (ai_providers_config)
CREATE TABLE IF NOT EXISTS ai_providers_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    provider_type VARCHAR(50) NOT NULL,
    provider_name VARCHAR(100) NOT NULL,
    active_model_name VARCHAR(150) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT 1,
    priority INTEGER NOT NULL DEFAULT 1,
    timeout_ms INTEGER NOT NULL DEFAULT 15000,
    cost_per_1k_tokens DECIMAL(8, 6) DEFAULT 0.000000,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 17. PROMPT ŞABLONLARI (prompt_templates)
CREATE TABLE IF NOT EXISTS prompt_templates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    template_key VARCHAR(100) UNIQUE NOT NULL,
    system_prompt TEXT NOT NULL,
    user_prompt_template TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT 1,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 18. YASAKLI İÇERİKLER (content_blacklists)
CREATE TABLE IF NOT EXISTS content_blacklists (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    keyword VARCHAR(100) UNIQUE NOT NULL,
    category VARCHAR(50) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    replacement_word VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 19. YÖNETİCİ DENETİM İZLERİ (admin_audit_logs)
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    admin_id INTEGER NOT NULL REFERENCES admins(id),
    action VARCHAR(100) NOT NULL,
    target_entity VARCHAR(100) NOT NULL,
    target_id INTEGER,
    details TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 20. EBEVEYN KİLİDİ KAYITLARI (parental_gate_attempts)
CREATE TABLE IF NOT EXISTS parental_gate_attempts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    parent_id INTEGER NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    challenge_question VARCHAR(100) NOT NULL,
    is_success BOOLEAN NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- İNDEKSLER
CREATE INDEX IF NOT EXISTS idx_admins_email ON admins(email);
CREATE INDEX IF NOT EXISTS idx_parents_email ON parents(email);
CREATE INDEX IF NOT EXISTS idx_child_profiles_parent_id ON child_profiles(parent_id);
CREATE INDEX IF NOT EXISTS idx_stories_child_id ON stories(child_id);
CREATE INDEX IF NOT EXISTS idx_stories_parent_id ON stories(parent_id);
CREATE INDEX IF NOT EXISTS idx_story_scenes_story_id ON story_scenes(story_id);
CREATE INDEX IF NOT EXISTS idx_character_bibles_child_id ON character_bibles(child_id);
"""

cursor.executescript(ddl_script)

# Insert initial default roles if not present
cursor.execute("INSERT OR IGNORE INTO roles (id, name, display_name, description) VALUES (1, 'ROLE_PARENT', 'Ebeveyn', 'Masal üreten ve çocuk profili yöneten standart ebeveyn hesabı');")
cursor.execute("INSERT OR IGNORE INTO roles (id, name, display_name, description) VALUES (2, 'ROLE_ADMIN', 'Yönetici', 'Sistem, içerik denetimi ve moderasyon yetkilisi');")
cursor.execute("INSERT OR IGNORE INTO roles (id, name, display_name, description) VALUES (3, 'ROLE_SUPER_ADMIN', 'Süper Yönetici', 'Tam yetkili sistem yöneticisi');")

# Insert initial subscription plans
cursor.execute("""
INSERT OR IGNORE INTO subscription_plans (id, plan_code, name, price, monthly_story_credits, interactive_credits, max_child_profiles, storage_retention_days, is_active)
VALUES 
(1, 'FREE_TIER', 'Ücretsiz Başlangıç', 0.00, 3, 1, 1, 7, 1),
(2, 'STANDARD_MONTHLY', 'Standart Masal Paketi', 149.99, 15, 5, 2, 180, 1),
(3, 'FAMILY_VIP', 'Aile VIP & Sınırsız Arşiv', 299.99, 40, 15, 5, 3650, 1);
""")

conn.commit()

# Verify created tables
cursor.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;")
tables = [row[0] for row in cursor.fetchall() if not row[0].startswith('sqlite_')]
print(f"Başarıyla oluşturulan tablo sayısı: {len(tables)}")
for idx, tbl in enumerate(tables, 1):
    print(f"{idx}. {tbl}")

conn.close()
