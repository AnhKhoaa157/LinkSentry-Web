package com.lyanhkhoa.linksentry.license.api;

import com.lyanhkhoa.linksentry.license.domain.DeviceState;
import java.time.Instant;
import java.util.UUID;

/** Admin-facing device detail, addressed by device ID or activation code rather than a credential. */
public record DeviceLookupResponse(
        UUID deviceId,
        String activationCode,
        String clientLabel,
        DeviceState state,
        UUID licenseId,
        Instant createdAt) {}
