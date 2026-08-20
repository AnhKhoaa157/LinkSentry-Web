package com.lyanhkhoa.linksentry.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Optional operator credential, bound from {@code linksentry.admin.api-key}
 * ({@code ADMIN_API_KEY}). Never sent to, or reachable from, the public frontend or extension.
 *
 * <p>May be blank at startup, the same tolerant-boot pattern {@code ResendMailProperties} uses: a
 * deployment without it still boots and serves every public route, while {@code
 * common.security.AdminApiKeyFilter} rejects only admin requests that do not carry a valid browser
 * administrator session.
 *
 * @param apiKey secret; read only from environment, never logged, never returned
 */
@Validated
@ConfigurationProperties(prefix = "linksentry.admin")
public record AdminProperties(String apiKey) {}
