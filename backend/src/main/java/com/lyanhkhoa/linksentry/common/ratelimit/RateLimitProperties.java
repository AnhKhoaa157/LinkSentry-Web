package com.lyanhkhoa.linksentry.common.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Rate-limit policy, bound from {@code linksentry.ratelimit.*}.
 *
 * <p>Every field is required and validated so a misconfigured deployment fails at
 * startup rather than silently running unlimited or with a broken bucket.
 *
 * @param enabled    turns rate limiting on or off entirely; when {@code false} no
 *                   request is classified, consumed, or delayed
 * @param scan        bucket applied to {@code POST /api/v1/scans}
 * @param scanLookup  bucket applied to {@code GET /api/v1/scans/{scanId}}
 * @param auth        stricter bucket applied to account/session routes
 * @param explanation strictest bucket, applied to
 *                    {@code POST /api/v1/scans/{scanId}/explanation}
 * @param store       bounds on the in-memory identity store backing all buckets
 */
@Validated
@ConfigurationProperties(prefix = "linksentry.ratelimit")
public record RateLimitProperties(
        boolean enabled,
        @NotNull @Valid Bucket scan,
        @NotNull @Valid Bucket scanLookup,
        @NotNull @Valid Bucket auth,
        @NotNull @Valid Bucket explanation,
        @NotNull @Valid Store store) {

    /**
     * One token bucket's shape. Refill is greedy (tokens trickle in continuously
     * rather than arriving in one batch every minute) so a client never sees a
     * thundering-herd reset at a fixed clock boundary.
     *
     * @param capacity        maximum tokens the bucket can hold, and the burst size
     * @param refillPerMinute tokens added per minute, spread continuously
     */
    public record Bucket(@Min(1) int capacity, @Min(1) int refillPerMinute) {}

    /**
     * Bounds on the per-client-address bucket store. Both limits exist for the same
     * reason: an attacker who controls many distinct source addresses must not be
     * able to grow this store without bound.
     *
     * @param maxEntries     hard cap on tracked addresses per route; the
     *                       least-recently-used entry is evicted once exceeded
     * @param idleExpiration how long an address may sit idle before its bucket is
     *                       proactively swept, freeing memory ahead of the hard cap
     */
    public record Store(@Min(1) int maxEntries, @NotNull Duration idleExpiration) {}
}
