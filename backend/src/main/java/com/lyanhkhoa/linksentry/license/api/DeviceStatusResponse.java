package com.lyanhkhoa.linksentry.license.api;

import com.lyanhkhoa.linksentry.license.domain.DeviceState;
import java.time.Instant;

/**
 * Response of {@code GET /api/v1/devices/me}.
 *
 * @param licenseExpiresAt present only while {@code state} is {@code LICENSED} or {@code EXPIRED};
 *                          {@code null} otherwise, including for a license with no expiry
 */
public record DeviceStatusResponse(DeviceState state, String activationCode, Instant licenseExpiresAt) {}
