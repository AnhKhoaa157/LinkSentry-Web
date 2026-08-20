package com.lyanhkhoa.linksentry.license.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Identity installed by {@code common.security.DeviceAuthenticationFilter} only when the presenting
 * device currently holds an active, non-expired license. It contains no raw credential.
 *
 * <p>{@code licenseId} is the scan-history ownership key: every device granted the same license shares
 * that license's retained history, which is how a web and an extension installation granted under one
 * license see each other's scans.
 */
public record LicensedDeviceContext(UUID deviceId, UUID licenseId, Instant licenseExpiresAt) {}
