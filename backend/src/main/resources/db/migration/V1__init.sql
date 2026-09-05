-- Core v1 schema (M1). Auth/clicks/claim tables exist so later milestones only add indexes/columns if needed.

CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE api_keys (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    prefix VARCHAR(16) NOT NULL,
    scopes JSONB NOT NULL DEFAULT '["read"]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_api_keys_user_id ON api_keys (user_id);

CREATE TABLE urls (
    id BIGINT PRIMARY KEY,
    short_code VARCHAR(30) NOT NULL,
    owner_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    destination_url TEXT NOT NULL,
    custom_alias BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMPTZ,
    max_clicks INTEGER,
    password_hash VARCHAR(255),
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    public_click_count BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_urls_short_code_lower ON urls (LOWER(short_code));
CREATE INDEX idx_urls_owner_id ON urls (owner_id);

CREATE TABLE clicks (
    id BIGINT PRIMARY KEY,
    url_id BIGINT NOT NULL REFERENCES urls (id) ON DELETE CASCADE,
    stream_id VARCHAR(64) UNIQUE,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_hash VARCHAR(64) NOT NULL,
    country VARCHAR(8),
    device_type VARCHAR(32),
    browser VARCHAR(64),
    os VARCHAR(64),
    referrer TEXT,
    is_bot BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_clicks_url_id_timestamp ON clicks (url_id, timestamp);
CREATE INDEX idx_clicks_timestamp ON clicks (timestamp);

CREATE TABLE guest_claim_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    url_id BIGINT NOT NULL REFERENCES urls (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_guest_claim_tokens_url_id ON guest_claim_tokens (url_id);
