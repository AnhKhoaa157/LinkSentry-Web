-- Administrator accounts for the protected /admin dashboard. Wholly separate
-- from user_account/auth_session (end-user model, being retired by another
-- change) and from linksentry.admin's ADMIN_API_KEY (an operator automation
-- secret for /api/v1/admin/**, unaffected by this migration). Passwords and
-- bearer session tokens are represented only by one-way hashes; the raw
-- values never have a column, and this migration seeds no account or secret.
CREATE TABLE admin_user (
    admin_user_id UUID PRIMARY KEY,
    username VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX admin_user_username_uq ON admin_user (username);

CREATE TABLE admin_session (
    session_id UUID PRIMARY KEY,
    admin_user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT admin_session_admin_user_fk
        FOREIGN KEY (admin_user_id) REFERENCES admin_user (admin_user_id) ON DELETE CASCADE,
    CONSTRAINT admin_session_token_hash_uq UNIQUE (token_hash),
    CONSTRAINT admin_session_expiry_check CHECK (expires_at > created_at)
);

CREATE INDEX admin_session_admin_user_idx ON admin_session (admin_user_id);
CREATE INDEX admin_session_active_lookup_idx ON admin_session (token_hash, expires_at, revoked_at);
