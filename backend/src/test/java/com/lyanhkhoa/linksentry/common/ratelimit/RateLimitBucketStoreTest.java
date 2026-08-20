package com.lyanhkhoa.linksentry.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the store's storage semantics (per-key identity, bounded eviction, idle
 * expiration) and, through the package-private bucket factory, the underlying
 * token-bucket timing (exhaustion and greedy refill).
 */
class RateLimitBucketStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");

    @Test
    @DisplayName("the same identity and route always return the same bucket, so state persists across calls")
    void sameIdentityAndRouteShareState() {
        RateLimitBucketStore store = newStore(properties(1, 100, Duration.ofMinutes(10)));

        Bucket first = store.bucketFor("203.0.113.5", RateLimitedRoute.SCAN_CREATE);
        assertThat(first.tryConsumeAndReturnRemaining(1).isConsumed()).isTrue();

        Bucket second = store.bucketFor("203.0.113.5", RateLimitedRoute.SCAN_CREATE);
        assertThat(second.tryConsumeAndReturnRemaining(1).isConsumed()).isFalse();
    }

    @Test
    @DisplayName("different identities never share a bucket")
    void differentIdentitiesAreIndependent() {
        RateLimitBucketStore store = newStore(properties(1, 100, Duration.ofMinutes(10)));

        assertThat(store.bucketFor("203.0.113.5", RateLimitedRoute.SCAN_CREATE)
                        .tryConsumeAndReturnRemaining(1)
                        .isConsumed())
                .isTrue();
        assertThat(store.bucketFor("198.51.100.9", RateLimitedRoute.SCAN_CREATE)
                        .tryConsumeAndReturnRemaining(1)
                        .isConsumed())
                .isTrue();
    }

    @Test
    @DisplayName("POST and GET buckets for the same identity are independent")
    void routesAreIndependentPerIdentity() {
        RateLimitBucketStore store = newStore(properties(1, 100, Duration.ofMinutes(10)));

        assertThat(store.bucketFor("203.0.113.5", RateLimitedRoute.SCAN_CREATE)
                        .tryConsumeAndReturnRemaining(1)
                        .isConsumed())
                .isTrue();
        assertThat(store.bucketFor("203.0.113.5", RateLimitedRoute.SCAN_LOOKUP)
                        .tryConsumeAndReturnRemaining(1)
                        .isConsumed())
                .isTrue();
    }

    @Test
    @DisplayName("tracked identities never exceed the configured hard cap; the least-recently-used is evicted")
    void hardCapEvictsLeastRecentlyUsed() {
        RateLimitBucketStore store = newStore(properties(1, 2, Duration.ofMinutes(10)));

        Bucket addr1 = store.bucketFor("addr-1", RateLimitedRoute.SCAN_CREATE);
        assertThat(addr1.tryConsumeAndReturnRemaining(1).isConsumed()).isTrue(); // addr-1's only token is now spent
        store.bucketFor("addr-2", RateLimitedRoute.SCAN_CREATE); // addr-1 is now the least recently touched
        assertThat(store.trackedIdentityCount()).isEqualTo(2);

        store.bucketFor("addr-3", RateLimitedRoute.SCAN_CREATE); // exceeds maxEntries=2; evicts addr-1
        assertThat(store.trackedIdentityCount()).isEqualTo(2);

        // If addr-1's exhausted bucket had survived, this consume would still fail.
        // A fresh, full-capacity bucket proves eviction actually happened.
        Bucket addr1Again = store.bucketFor("addr-1", RateLimitedRoute.SCAN_CREATE);
        assertThat(addr1Again.tryConsumeAndReturnRemaining(1).isConsumed()).isTrue();
    }

    @Test
    @DisplayName("an idle entry is swept once past the configured expiration, directly and without a scheduler tick")
    void idleEntryIsSweptAfterExpiration() {
        MutableClock clock = new MutableClock(NOW);
        RateLimitBucketStore store = new RateLimitBucketStore(properties(1, 100, Duration.ofMinutes(10)), clock);

        Bucket bucket = store.bucketFor("203.0.113.5", RateLimitedRoute.SCAN_CREATE);
        assertThat(bucket.tryConsumeAndReturnRemaining(1).isConsumed()).isTrue(); // exhausts capacity 1
        assertThat(store.trackedIdentityCount()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(9));
        store.evictIdle();
        assertThat(store.trackedIdentityCount()).isEqualTo(1); // not idle long enough yet

        clock.advance(Duration.ofMinutes(2)); // 11 minutes total, past the 10-minute idle-expiration
        store.evictIdle();
        assertThat(store.trackedIdentityCount()).isZero();

        // A fresh bucket for the same key starts with full capacity again.
        assertThat(store.bucketFor("203.0.113.5", RateLimitedRoute.SCAN_CREATE)
                        .tryConsumeAndReturnRemaining(1)
                        .isConsumed())
                .isTrue();
    }

    @Test
    @DisplayName("a bucket allows exactly its capacity, rejects the next, then allows again once refilled")
    void exhaustionThenRefill() {
        RateLimitProperties.Bucket config = new RateLimitProperties.Bucket(2, 60); // capacity 2, 60/min = 1/sec
        MutableTimeMeter timeMeter = new MutableTimeMeter(0L);
        Bucket bucket = RateLimitBucketStore.newBucket(config, timeMeter);

        assertThat(bucket.tryConsumeAndReturnRemaining(1).isConsumed()).isTrue();
        assertThat(bucket.tryConsumeAndReturnRemaining(1).isConsumed()).isTrue();
        assertThat(bucket.tryConsumeAndReturnRemaining(1).isConsumed()).isFalse(); // exhausted

        timeMeter.advance(Duration.ofSeconds(1)); // greedy refill: exactly 1 token back
        assertThat(bucket.tryConsumeAndReturnRemaining(1).isConsumed()).isTrue();
        assertThat(bucket.tryConsumeAndReturnRemaining(1).isConsumed()).isFalse();
    }

    private static RateLimitBucketStore newStore(RateLimitProperties properties) {
        return new RateLimitBucketStore(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RateLimitProperties properties(int capacity, int maxEntries, Duration idleExpiration) {
        RateLimitProperties.Bucket bucket = new RateLimitProperties.Bucket(capacity, 60);
        return new RateLimitProperties(
                true, bucket, bucket, bucket, bucket, bucket, bucket,
                new RateLimitProperties.Store(maxEntries, idleExpiration));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class MutableTimeMeter implements TimeMeter {
        private long nanos;

        private MutableTimeMeter(long initialNanos) {
            this.nanos = initialNanos;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }
}
