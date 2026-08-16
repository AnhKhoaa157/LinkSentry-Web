package com.lyanhkhoa.linksentry.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cross-origin settings, bound from {@code linksentry.cors.*}.
 *
 * <p>Validated at startup so a missing or empty origin list fails the boot
 * rather than silently producing an API no browser can call. A wildcard origin is
 * never acceptable for a deployed environment: this API is intended to be callable
 * only by the LinkSentry frontend.
 *
 * @param allowedOrigins exact origins permitted to call the API, e.g.
 *                       {@code http://localhost:5173}
 * @param allowedMethods HTTP methods permitted for cross-origin requests
 */
@Validated
@ConfigurationProperties(prefix = "linksentry.cors")
public record CorsProperties(
        @NotEmpty List<@NotBlank String> allowedOrigins, @NotEmpty List<@NotBlank String> allowedMethods) {}
