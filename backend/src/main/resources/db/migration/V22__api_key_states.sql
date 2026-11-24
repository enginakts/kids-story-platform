-- API anahtarı yönetimi (V22). Anahtarların kendisi veritabanına yazılmaz; .env'de durur
-- (GEMINI_API_KEY, GEMINI_API_KEY_2, ...). Burada yalnızca her servisteki her anahtarın durumu tutulur:
-- yönetici kapattı mı, kota/yetki hatasıyla otomatik kapandı mı, ne zaman yeniden açılacak, bugünkü kullanım.
-- Servis = sağlayıcı adı (gemini, gemini-lite, gemini-tts, groq, cloudflare, murf, azure, featherless): aynı Gemini
-- anahtarı ses kotası bitince yalnızca seste kapanır, sahne bölmede kullanılmaya devam eder.
CREATE TABLE api_key_states (
    id              BIGSERIAL PRIMARY KEY,
    service         VARCHAR(50) NOT NULL,
    slot            SMALLINT    NOT NULL CHECK (slot BETWEEN 1 AND 9),
    enabled         BOOLEAN     NOT NULL DEFAULT TRUE,
    disabled_by     VARCHAR(10) CHECK (disabled_by IN ('ADMIN', 'AUTO')),
    disabled_reason VARCHAR(200),
    disabled_at     TIMESTAMPTZ,
    reenable_at     TIMESTAMPTZ,
    last_error      VARCHAR(200),
    last_error_at   TIMESTAMPTZ,
    last_used_at    TIMESTAMPTZ,
    calls_day       DATE,
    calls_today     INT         NOT NULL DEFAULT 0,
    updated_by      BIGINT REFERENCES admins (id) ON DELETE SET NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_api_key_states UNIQUE (service, slot)
);
