package com.lyanhkhoa.linksentry.license.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body of {@code POST /api/v1/admin/licenses/{licenseId}/devices}. */
public record GrantDeviceRequest(
        @NotBlank(message = "Enter the device's activation code.")
                @Size(max = 32, message = "Enter a valid activation code.")
                String activationCode) {}
