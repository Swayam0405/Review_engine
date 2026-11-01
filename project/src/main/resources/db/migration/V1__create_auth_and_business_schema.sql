CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(320) NOT NULL,
    account_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_account_status CHECK (account_status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED'))
);

CREATE TABLE auth_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100),
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_auth_identities_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT ck_auth_identity_provider CHECK (provider IN ('EMAIL_PASSWORD', 'GOOGLE', 'PHONE_OTP')),
    CONSTRAINT ck_auth_identity_password CHECK (
        (provider = 'EMAIL_PASSWORD' AND password_hash IS NOT NULL)
        OR (provider <> 'EMAIL_PASSWORD' AND password_hash IS NULL)
    )
);

CREATE INDEX ix_auth_identities_user_id ON auth_identities(user_id);

CREATE TABLE email_verification_otps (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    auth_identity_id UUID NOT NULL REFERENCES auth_identities(id) ON DELETE CASCADE,
    otp_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    verified_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_email_otp_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_email_otp_identity_created ON email_verification_otps(auth_identity_id, created_at DESC);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_token_id UUID REFERENCES refresh_tokens(id),
    created_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT uk_refresh_tokens_replaced_by UNIQUE (replaced_by_token_id)
);

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens(user_id);

CREATE TABLE businesses (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    category VARCHAR(100) NOT NULL,
    address VARCHAR(500) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    google_review_url VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_businesses_owner_user UNIQUE (owner_user_id)
);
