package com.lyanhkhoa.linksentry.license.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One grant of a device to a license.
 *
 * <p>A device may accumulate several of these across revoke/re-grant; at most one may be active ({@code
 * revokedAt == null}) at a time. Revoking a specific device revokes only its current active assignment —
 * the device installation itself is untouched and may be granted again later.
 */
public record DeviceLicenseAssignment(
        UUID assignmentId, UUID licenseId, UUID deviceId, Instant grantedAt, Instant revokedAt) {

    public DeviceLicenseAssignment {
        Objects.requireNonNull(assignmentId, "assignmentId");
        Objects.requireNonNull(licenseId, "licenseId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(grantedAt, "grantedAt");
    }

    public boolean isActive() {
        return revokedAt == null;
    }
}
