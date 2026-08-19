package com.lyanhkhoa.linksentry.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Expiry and brute-force limits for email registration verification. */
@Validated
@ConfigurationProperties(prefix = "linksentry.auth.otp")
public record AuthOtpProperties(@NotNull Duration ttl, @Min(1) int maxAttempts, String mailFrom) {

    public AuthOtpProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("otp.ttl must be positive");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("otp.maxAttempts must be positive");
        }
    }
}
