package com.lyanhkhoa.linksentry.license.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for device/license grant history. */
public interface DeviceLicenseAssignmentRepository {

    void save(DeviceLicenseAssignment assignment);

    /** The one assignment currently in effect for a device, if any ({@code revokedAt IS NULL}). */
    Optional<DeviceLicenseAssignment> findActiveByDeviceId(UUID deviceId);

    /**
     * The most recent assignment for a device regardless of revocation, so a revoked device can be told
     * apart from one that was never granted at all.
     */
    Optional<DeviceLicenseAssignment> findLatestByDeviceId(UUID deviceId);

    /** Every currently active assignment under a license, for admin display. */
    List<DeviceLicenseAssignment> findActiveByLicenseId(UUID licenseId);

    long countActiveByLicenseId(UUID licenseId);

    /** No-op when the assignment is already revoked. */
    void revoke(UUID assignmentId, Instant revokedAt);
}
