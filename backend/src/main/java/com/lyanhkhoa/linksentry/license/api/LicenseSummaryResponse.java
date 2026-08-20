package com.lyanhkhoa.linksentry.license.api;

import java.time.Instant;
import java.util.UUID;

/** One row of {@code GET /api/v1/admin/licenses}; omits the device list to keep listing cheap. */
public record LicenseSummaryResponse(
        UUID licenseId,
        String label,
        Instant expiresAt,
        int maxDevices,
        boolean revoked,
        Instant createdAt,
        long activeDeviceCount) {}
