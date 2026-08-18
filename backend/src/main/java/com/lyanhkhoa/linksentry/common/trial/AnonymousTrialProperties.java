package com.lyanhkhoa.linksentry.common.trial;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Anonymous scan trial policy, bound from {@code linksentry.anonymous-trial.*}.
 *
 * <p>Every field is required and validated so a misconfigured deployment fails at
 * startup rather than silently running an unbounded or broken trial quota. Beyond
 * the per-field checks, {@code store.idleExpiration} must be at least {@code window}:
 * a shorter idle expiration can evict a still-active address entry before its
 * accepted timestamps age out of the rolling window, granting a fresh quota earlier
 * than configured.
 *
 * @param enabled  turns the trial guard on or off entirely; when {@code false} every
 *                 anonymous {@code POST /api/v1/scans} passes through unchecked
 * @param maxScans maximum accepted anonymous scans per remote address within
 *                 {@code window}
 * @param window   rolling duration the quota is evaluated over; must be positive
 * @param store    bounds on the in-memory identity store backing the quota
 */
@Validated
@ConfigurationProperties(prefix = "linksentry.anonymous-trial")
public record AnonymousTrialProperties(
        boolean enabled,
        @Min(1) int maxScans,
        @NotNull @DurationMin(nanos = 1) Duration window,
        @NotNull @Valid Store store) {

    public AnonymousTrialProperties {
        if (window != null && store != null && store.idleExpiration() != null
                && store.idleExpiration().compareTo(window) < 0) {
            throw new IllegalArgumentException(
                    "linksentry.anonymous-trial.store.idle-expiration must be greater than or equal to "
                            + "linksentry.anonymous-trial.window");
        }
    }

    /**
     * Bounds on the per-remote-address trial store. Both limits exist for the same
     * reason as {@code RateLimitProperties.Store}: an attacker who controls many
     * distinct source addresses must not be able to grow this store without bound.
     *
     * @param maxEntries     hard cap on tracked addresses; the least-recently-used
     *                       entry is evicted once exceeded
     * @param idleExpiration how long an address may sit idle before its entry is
     *                       proactively swept, freeing memory ahead of the hard cap;
     *                       must be positive and at least {@code window}
     */
    public record Store(@Min(1) int maxEntries, @NotNull @DurationMin(nanos = 1) Duration idleExpiration) {}
}
