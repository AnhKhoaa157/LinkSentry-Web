-- Pending registrations contain only one-way hashes. The account is created
-- after the submitted email's verification code is checked successfully.
CREATE TABLE auth_registration_verification (
    email VARCHAR(320) PRIMARY KEY,
    password_hash VARCHAR(100) NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT auth_registration_attempts_check CHECK (attempts >= 0)
);

CREATE INDEX auth_registration_expiry_idx
    ON auth_registration_verification (expires_at);
