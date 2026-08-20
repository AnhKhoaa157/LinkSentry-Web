package com.lyanhkhoa.linksentry.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Device-licensing defaults, bound from {@code linksentry.license.*}.
 *
 * @param defaultMaxDevices used when an admin creates a license without specifying {@code maxDevices};
 *                          the documented default is 2 (one web installation plus one extension)
 * @param pendingDeviceRetention how long a device installation that was never granted any license is
 *                          kept before {@code license.application.DevicePendingRetentionService} deletes
 *                          it. Bounds unbounded table growth from the public, unauthenticated {@code
 *                          POST /api/v1/devices}. A device with any assignment history — even a revoked
 *                          one — is never affected by this window; only a device that has never been
 *                          assigned is a deletion candidate.
 */
@Validated
@ConfigurationProperties(prefix = "linksentry.license")
public record LicenseProperties(@Min(1) int defaultMaxDevices, @NotNull Duration pendingDeviceRetention) {

    public LicenseProperties {
        if (pendingDeviceRetention == null || pendingDeviceRetention.isZero() || pendingDeviceRetention.isNegative()) {
            throw new IllegalArgumentException("pendingDeviceRetention must be positive");
        }
    }
}
