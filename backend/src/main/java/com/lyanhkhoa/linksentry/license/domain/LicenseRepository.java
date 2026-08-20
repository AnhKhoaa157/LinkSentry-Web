package com.lyanhkhoa.linksentry.license.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for licenses. */
public interface LicenseRepository {

    void save(License license);

    Optional<License> findById(UUID licenseId);

    /**
     * Same lookup as {@link #findById}, but holds a database-level pessimistic write lock on the
     * license row until the enclosing transaction ends. Callers that check-then-insert against this
     * license's device cap (see {@code LicenseAdminService#grantDevice}) must use this instead of
     * {@link #findById}: the lock serializes concurrent grants for the same license, so a
     * count-then-insert cannot oversubscribe {@code maxDevices} no matter how many admin requests for
     * the same license race each other. Read-only callers (e.g. status lookups) must keep using the
     * unlocked {@link #findById} — locking there would only add contention with no invariant to protect.
     */
    Optional<License> findByIdForUpdate(UUID licenseId);

    List<License> findAll();

    /** Updates only {@code expiresAt}; {@code null} means no expiry. */
    void updateExpiry(UUID licenseId, Instant expiresAt);

    /** No-op when the license is already revoked. */
    void revoke(UUID licenseId, Instant revokedAt);
}
