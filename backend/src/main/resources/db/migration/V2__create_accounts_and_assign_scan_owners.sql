-- Accounts use normalized email addresses. Passwords and bearer values are
-- represented only by one-way hashes; the raw values never have a column.
CREATE TABLE user_account (
    user_id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT user_account_email_lower_check CHECK (email = lower(email))
);

CREATE UNIQUE INDEX user_account_email_uq ON user_account (email);

CREATE TABLE auth_session (
    session_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT auth_session_user_fk
        FOREIGN KEY (user_id) REFERENCES user_account (user_id) ON DELETE CASCADE,
    CONSTRAINT auth_session_token_hash_uq UNIQUE (token_hash),
    CONSTRAINT auth_session_expiry_check CHECK (expires_at > created_at)
);

CREATE INDEX auth_session_user_idx ON auth_session (user_id);
CREATE INDEX auth_session_active_lookup_idx ON auth_session (token_hash, expires_at, revoked_at);

-- V1 rows intentionally remain ownerless. The owner predicate in the history
-- repository never returns these rows to an authenticated user.
ALTER TABLE scan_history ADD COLUMN owner_user_id UUID;

ALTER TABLE scan_history
    ADD CONSTRAINT scan_history_owner_user_fk
    FOREIGN KEY (owner_user_id) REFERENCES user_account (user_id) ON DELETE SET NULL;

CREATE INDEX scan_history_owner_analyzed_at_idx
    ON scan_history (owner_user_id, analyzed_at);
