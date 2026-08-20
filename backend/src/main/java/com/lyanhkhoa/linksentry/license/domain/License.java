package com.lyanhkhoa.linksentry.license.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An administrator-created grant of access, independent of any specific device.
 *
 * @param expiresAt   {@code null} means no expiry
 * @param maxDevices  maximum number of devices that may hold an active {@link DeviceLicenseAssignment}
 *                    against this license at once
 * @param revokedAt   {@code null} while active; once set, every device under this license loses access
 *                    on its next request
 */
public record License(
        UUID licenseId, String label, Instant expiresAt, int maxDevices, Instant revokedAt, Instant createdAt) {

    public License {
        Objects.requireNonNull(licenseId, "licenseId");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(createdAt, "createdAt");
        if (maxDevices < 1) {
            throw new IllegalArgumentException("maxDevices must be at least 1");
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /** Active means neither revoked nor expired as of {@code now}. */
    public boolean isActive(Instant now) {
        return !isRevoked() && !isExpired(now);
    }
}
