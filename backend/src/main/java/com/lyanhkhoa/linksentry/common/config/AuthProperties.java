package com.lyanhkhoa.linksentry.common.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuration for stateless, opaque bearer sessions. */
@Validated
@ConfigurationProperties(prefix = "linksentry.auth")
public record AuthProperties(@NotNull Duration sessionTtl) {

    public AuthProperties {
        if (sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("sessionTtl must be positive");
        }
    }
}
