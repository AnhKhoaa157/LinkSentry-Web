package com.lyanhkhoa.linksentry.license.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/v1/devices}.
 *
 * @param clientLabel optional, client-reported display hint (e.g. {@code "web"} or {@code "extension"});
 *                     purely cosmetic for an administrator's own reference and never used in any
 *                     entitlement decision
 */
public record DeviceBootstrapRequest(
        @Size(max = 32, message = "Use a label of 32 characters or fewer.")
                @Pattern(regexp = "^[A-Za-z0-9 _-]*$", message = "Use only letters, numbers, spaces, hyphens, or underscores.")
                String clientLabel) {}
