package com.lyanhkhoa.linksentry.license.api;

import java.time.Instant;

/** Request body of {@code POST /api/v1/admin/licenses/{licenseId}/extend}; {@code null} means no expiry. */
public record ExtendLicenseRequest(Instant expiresAt) {}
