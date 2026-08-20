-- Device-installation and admin-granted licensing replaces email/password/OTP
-- authentication. A device authenticates with a high-entropy credential; only
-- its SHA-256 hash is ever persisted. A license is created and granted only
-- through administrator-authenticated admin endpoints; ADMIN_API_KEY remains an
-- operator automation option and is never exposed to a browser.
CREATE TABLE license (
    license_id UUID PRIMARY KEY,
    label VARCHAR(200) NOT NULL,
    expires_at TIMESTAMPTZ,
    max_devices INTEGER NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT license_max_devices_check CHECK (max_devices >= 1)
);

-- One row per independent web or extension installation. activation_code is
-- safe to copy and hand to an administrator; credential_hash is the only
-- representation of the client's secret device credential ever stored.
CREATE TABLE device_installation (
    device_id UUID PRIMARY KEY,
    activation_code VARCHAR(32) NOT NULL,
    credential_hash VARCHAR(64) NOT NULL,
    client_label VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX device_installation_activation_code_uq ON device_installation (activation_code);
CREATE UNIQUE INDEX device_installation_credential_hash_uq ON device_installation (credential_hash);

-- One row per grant of a device to a license. A device may accumulate several
-- historical rows across revoke/re-grant; at most one may be active
-- (revoked_at IS NULL) at any time, enforced by the partial unique index
-- below rather than application logic alone.
CREATE TABLE device_license_assignment (
    assignment_id UUID PRIMARY KEY,
    license_id UUID NOT NULL,
    device_id UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT device_license_assignment_license_fk
        FOREIGN KEY (license_id) REFERENCES license (license_id) ON DELETE CASCADE,
    CONSTRAINT device_license_assignment_device_fk
        FOREIGN KEY (device_id) REFERENCES device_installation (device_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX device_license_assignment_active_device_uq
    ON device_license_assignment (device_id) WHERE revoked_at IS NULL;
CREATE INDEX device_license_assignment_license_idx ON device_license_assignment (license_id);
CREATE INDEX device_license_assignment_device_idx ON device_license_assignment (device_id, granted_at);

-- Scan ownership moves from an authenticated account to the license a device
-- is granted under, so a license's web and extension installations can share
-- retained history. This is additive only: V1's owner_user_id column, and the
-- V2/V3 auth tables, are retired in place, not dropped or rewritten. Legacy
-- authenticated history remains stored but is never read by any code path
-- after this migration, the same "preserved but permanently unreadable"
-- pattern V2 already established for ownerless V1 rows.
ALTER TABLE scan_history ADD COLUMN owner_license_id UUID;

ALTER TABLE scan_history
    ADD CONSTRAINT scan_history_owner_license_fk
    FOREIGN KEY (owner_license_id) REFERENCES license (license_id) ON DELETE SET NULL;

CREATE INDEX scan_history_owner_license_analyzed_at_idx
    ON scan_history (owner_license_id, analyzed_at);
