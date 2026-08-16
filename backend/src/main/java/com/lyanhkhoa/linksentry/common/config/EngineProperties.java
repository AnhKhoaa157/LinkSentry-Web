package com.lyanhkhoa.linksentry.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Identifies which version of the analysis engine produced a scan, bound from
 * {@code linksentry.engine.*}.
 *
 * <p>Carried in every scan response's {@code meta.engineVersion} so a result that
 * is shared or (eventually) stored can be interpreted correctly after the rules or
 * scoring bands change.
 *
 * @param version the engine version string, e.g. {@code 0.1.0}
 */
@Validated
@ConfigurationProperties(prefix = "linksentry.engine")
public record EngineProperties(@NotBlank String version) {}
