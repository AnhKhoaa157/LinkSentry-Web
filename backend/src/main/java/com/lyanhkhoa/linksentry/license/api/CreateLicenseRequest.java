package com.lyanhkhoa.linksentry.license.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Request body of {@code POST /api/v1/admin/licenses}.
 *
 * @param expiresAt   {@code null} means no expiry
 * @param maxDevices  {@code null} uses the configured {@code linksentry.license.default-max-devices}
 */
public record CreateLicenseRequest(
        @NotBlank(message = "Enter a label for this license.")
                @Size(max = 200, message = "Use a label of 200 characters or fewer.")
                String label,
        Instant expiresAt,
        @Min(value = 1, message = "maxDevices must be at least 1.") Integer maxDevices) {}
