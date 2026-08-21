package com.lyanhkhoa.linksentry.common.trial;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Anonymous scan trial policy, bound from {@code linksentry.anonymous-trial.*}.
 *
 * <p>Every field is required and validated so a misconfigured deployment fails at startup rather
 * than silently running an unbounded or broken trial quota. {@code store.*} (bounds on the old
 * in-memory identity map) was removed under ADR 0010: the quota is PostgreSQL-persisted now, so
 * there is no unbounded heap structure left to bound.
 *
 * @param enabled  turns the trial guard on or off entirely; when {@code false} every anonymous
 *                 {@code POST /api/v1/scans} passes through unchecked
 * @param maxScans maximum admitted trial scans per device within {@code window}
 * @param window   rolling duration the quota is evaluated over; must be positive
 */
@Validated
@ConfigurationProperties(prefix = "linksentry.anonymous-trial")
public record AnonymousTrialProperties(boolean enabled, @Min(1) int maxScans, @NotNull @DurationMin(nanos = 1) Duration window) {}
