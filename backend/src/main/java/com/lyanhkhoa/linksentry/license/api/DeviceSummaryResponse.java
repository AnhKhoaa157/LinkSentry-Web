package com.lyanhkhoa.linksentry.license.api;

import java.time.Instant;
import java.util.UUID;

/** One device currently active under a license, as shown in {@link LicenseResponse#devices()}. */
public record DeviceSummaryResponse(UUID deviceId, String activationCode, String clientLabel, Instant grantedAt) {}
