package com.lyanhkhoa.linksentry.admin.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for administrator bootstrap and opaque bearer sessions, bound from {@code
 * linksentry.admin-auth.*}.
 *
 * <p>{@code bootstrapUsername} and {@code bootstrapPassword} may be blank — the same
 * tolerant-boot pattern as {@code common.config.AdminProperties}'s {@code ADMIN_API_KEY}: a
 * deployment without them still boots and serves every route, but {@code
 * admin.application.AdminBootstrapRunner} then creates no administrator account, so every {@code
 * /api/v1/admin-auth/login} attempt fails with the same safe invalid-credentials response until
 * they are set and the backend restarts with still no admin account present.
 *
 * @param bootstrapUsername read only while no admin account exists yet; never logged or persisted raw
 * @param bootstrapPassword read only while no admin account exists yet; only its BCrypt hash is ever stored
 * @param sessionTtl admin login session lifetime
 */
@Validated
@ConfigurationProperties(prefix = "linksentry.admin-auth")
public record AdminAuthProperties(String bootstrapUsername, String bootstrapPassword, @NotNull Duration sessionTtl) {

    public AdminAuthProperties {
        if (sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("sessionTtl must be positive");
        }
    }
}
