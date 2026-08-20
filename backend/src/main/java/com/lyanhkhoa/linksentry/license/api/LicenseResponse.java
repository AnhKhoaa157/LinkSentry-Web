package com.lyanhkhoa.linksentry.license.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full detail for one license, returned by create and by-ID lookup. */
public record LicenseResponse(
        UUID licenseId,
        String label,
        Instant expiresAt,
        int maxDevices,
        boolean revoked,
        Instant createdAt,
        List<DeviceSummaryResponse> devices) {}
