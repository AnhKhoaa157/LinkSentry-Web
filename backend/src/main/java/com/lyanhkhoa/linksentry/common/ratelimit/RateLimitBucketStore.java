package com.lyanhkhoa.linksentry.common.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * One in-process token bucket per (client address, route), bounded so a client that
 * spreads requests across many distinct addresses cannot grow this store without
 * limit. State lives only in this instance's heap — see {@code docs/ARCHITECTURE.md}
 * for the single-instance caveat this implies.
 *
 * <p>Two independent safety nets keep it bounded: a hard cap evicts the
 * least-recently-used entry the moment {@code store.max-entries} would be exceeded,
 * and a scheduled sweep removes entries idle past {@code store.idle-expiration} so
 * memory is reclaimed well before the hard cap in the common case.
 */
@Component
public class RateLimitBucketStore {

    private final RateLimitProperties properties;
    private final Clock clock;
    private final Object lock = new Object();
    private final LinkedHashMap<BucketKey, Entry> entries;

    public RateLimitBucketStore(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        int maxEntries = properties.store().maxEntries();
        // accessOrder=true turns get() into an LRU touch; removeEldestEntry() then
        // fires from put() the moment a new key would push the map past maxEntries.
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BucketKey, Entry> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /** Returns the bucket for {@code identity}'s {@code route}, creating it on first use. */
    Bucket bucketFor(String identity, RateLimitedRoute route) {
        BucketKey key = new BucketKey(identity, route);
        Instant now = Instant.now(clock);
        synchronized (lock) {
            Entry entry = entries.get(key);
            if (entry == null) {
                entry = new Entry(newBucket(bucketConfig(route)));
                entries.put(key, entry);
            }
            entry.lastAccess = now;
            return entry.bucket;
        }
    }

    /**
     * Sweeps entries idle past {@code store.idle-expiration}. Scheduled every five
     * minutes and also directly callable from tests, so eviction never depends on
     * waiting for a scheduler tick. {@code @EnableScheduling} is already active
     * application-wide via {@code HistoryConfiguration}.
     */
    @Scheduled(fixedDelay = 5, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
    void evictIdle() {
        Instant cutoff = Instant.now(clock).minus(properties.store().idleExpiration());
        synchronized (lock) {
            entries.values().removeIf(entry -> entry.lastAccess.isBefore(cutoff));
        }
    }

    /** Number of client-address/route entries currently tracked. Test-only visibility hook. */
    int trackedIdentityCount() {
        synchronized (lock) {
            return entries.size();
        }
    }

    private RateLimitProperties.Bucket bucketConfig(RateLimitedRoute route) {
        return switch (route) {
            case SCAN_CREATE -> properties.scan();
            case SCAN_LOOKUP -> properties.scanLookup();
            case AUTH -> properties.auth();
        };
    }

    /** Builds a bucket using the JVM's real clock — production path. */
    static Bucket newBucket(RateLimitProperties.Bucket config) {
        return Bucket.builder()
                .addLimit(limit ->
                        limit.capacity(config.capacity()).refillGreedy(config.refillPerMinute(), Duration.ofMinutes(1)))
                .build();
    }

    /** Builds a bucket against a caller-supplied clock — deterministic refill tests only. */
    static Bucket newBucket(RateLimitProperties.Bucket config, TimeMeter timeMeter) {
        return Bucket.builder()
                .withCustomTimePrecision(timeMeter)
                .addLimit(limit ->
                        limit.capacity(config.capacity()).refillGreedy(config.refillPerMinute(), Duration.ofMinutes(1)))
                .build();
    }

    private static final class Entry {
        private final Bucket bucket;
        private volatile Instant lastAccess;

        private Entry(Bucket bucket) {
            this.bucket = bucket;
        }
    }

    private record BucketKey(String identity, RateLimitedRoute route) {}
}
