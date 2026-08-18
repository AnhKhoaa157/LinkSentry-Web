package com.lyanhkhoa.linksentry.common.trial;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the store's rolling-window semantics (per-address identity, boundary
 * behaviour, bounded eviction, idle expiration) with an injected {@link Clock} so
 * every timing assertion is deterministic rather than racing the wall clock.
 */
class AnonymousTrialStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

    @Test
    @DisplayName("allows exactly maxScans within the window, then rejects without recording further attempts")
    void allowsExactlyMaxScansThenRejects() {
        MutableClock clock = new MutableClock(NOW);
        AnonymousTrialStore store = new AnonymousTrialStore(properties(3, Duration.ofHours(24), 100, Duration.ofHours(24)), clock);

        assertThat(store.tryConsume("203.0.113.5")).isTrue();
        assertThat(store.tryConsume("203.0.113.5")).isTrue();
        assertThat(store.tryConsume("203.0.113.5")).isTrue();
        assertThat(store.tryConsume("203.0.113.5")).isFalse();
        // Rejection does not consume a slot: still exhausted, not further reduced.
        assertThat(store.tryConsume("203.0.113.5")).isFalse();
    }

    @Test
    @DisplayName("IPv4 and IPv6 addresses hold independent quotas")
    void ipv4AndIpv6AreIndependent() {
        AnonymousTrialStore store = newStore(1, Duration.ofHours(24));

        assertThat(store.tryConsume("203.0.113.5")).isTrue();
        assertThat(store.tryConsume("203.0.113.5")).isFalse();

        assertThat(store.tryConsume("2001:db8::1")).isTrue();
        assertThat(store.tryConsume("2001:db8::1")).isFalse();
    }

    @Test
    @DisplayName("different remote addresses never share a quota")
    void differentAddressesAreIndependent() {
        AnonymousTrialStore store = newStore(1, Duration.ofHours(24));

        assertThat(store.tryConsume("203.0.113.5")).isTrue();
        assertThat(store.tryConsume("198.51.100.9")).isTrue();
    }

    @Test
    @DisplayName("a scan at exactly the rolling-window boundary still counts; it ages out only once strictly past it")
    void boundaryScanStillCountsUntilStrictlyPastTheWindow() {
        MutableClock clock = new MutableClock(NOW);
        AnonymousTrialStore store = new AnonymousTrialStore(properties(1, Duration.ofHours(24), 100, Duration.ofHours(24)), clock);

        assertThat(store.tryConsume("203.0.113.5")).isTrue(); // t=0

        // One second before the window fully elapses: still inside it, still exhausted.
        clock.advance(Duration.ofHours(24).minusSeconds(1));
        assertThat(store.tryConsume("203.0.113.5")).isFalse();

        // Exactly 24h later (elapsed == window): the boundary is inclusive, still exhausted.
        clock.advance(Duration.ofSeconds(1));
        assertThat(store.tryConsume("203.0.113.5")).isFalse();

        // One second past the full window: the original scan has now aged out.
        clock.advance(Duration.ofSeconds(1));
        assertThat(store.tryConsume("203.0.113.5")).isTrue();
    }

    @Test
    @DisplayName("the rolling window slides: quota frees up as the oldest scan ages out, not at a fixed reset instant")
    void windowSlidesRatherThanResettingAtAFixedBoundary() {
        MutableClock clock = new MutableClock(NOW);
        AnonymousTrialStore store = new AnonymousTrialStore(properties(2, Duration.ofHours(24), 100, Duration.ofHours(24)), clock);

        assertThat(store.tryConsume("203.0.113.5")).isTrue(); // scan A at t=0
        clock.advance(Duration.ofHours(20));
        assertThat(store.tryConsume("203.0.113.5")).isTrue(); // scan B at t=20h, quota now full (2/2)
        assertThat(store.tryConsume("203.0.113.5")).isFalse(); // still t=20h, exhausted

        // t=24h+1s: scan A (elapsed 24h+1s, strictly past the window) has aged out; scan B
        // (elapsed 4h+1s) has not. One slot frees up — the window slid past A's age-out
        // instant, not a fixed "reset" tied to when the test started.
        clock.advance(Duration.ofHours(4).plusSeconds(1));
        assertThat(store.tryConsume("203.0.113.5")).isTrue();
        assertThat(store.tryConsume("203.0.113.5")).isFalse();
    }

    @Test
    @DisplayName("tracked addresses never exceed the configured hard cap; the least-recently-used is evicted")
    void hardCapEvictsLeastRecentlyUsed() {
        AnonymousTrialStore store = newStore(1, Duration.ofHours(24), 2, Duration.ofHours(24));

        store.tryConsume("addr-1"); // exhausts addr-1's only slot
        store.tryConsume("addr-2"); // addr-1 is now least recently touched
        assertThat(store.trackedIdentityCount()).isEqualTo(2);

        store.tryConsume("addr-3"); // exceeds maxEntries=2; evicts addr-1
        assertThat(store.trackedIdentityCount()).isEqualTo(2);

        // If addr-1's exhausted history had survived, this would still fail.
        assertThat(store.tryConsume("addr-1")).isTrue();
    }

    @Test
    @DisplayName("an idle entry is swept once past the configured expiration, directly and without a scheduler tick")
    void idleEntryIsSweptAfterExpiration() {
        MutableClock clock = new MutableClock(NOW);
        AnonymousTrialStore store =
                new AnonymousTrialStore(properties(1, Duration.ofMinutes(5), 100, Duration.ofMinutes(10)), clock);

        assertThat(store.tryConsume("203.0.113.5")).isTrue();
        assertThat(store.trackedIdentityCount()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(9));
        store.evictIdle();
        assertThat(store.trackedIdentityCount()).isEqualTo(1); // not idle long enough yet

        clock.advance(Duration.ofMinutes(2)); // 11 minutes total, past the 10-minute idle-expiration
        store.evictIdle();
        assertThat(store.trackedIdentityCount()).isZero();

        // A fresh entry for the same address starts with full quota again.
        assertThat(store.tryConsume("203.0.113.5")).isTrue();
    }

    private static AnonymousTrialStore newStore(int maxScans, Duration window) {
        return newStore(maxScans, window, 100, Duration.ofHours(24));
    }

    private static AnonymousTrialStore newStore(int maxScans, Duration window, int maxEntries, Duration idleExpiration) {
        return new AnonymousTrialStore(
                properties(maxScans, window, maxEntries, idleExpiration), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AnonymousTrialProperties properties(
            int maxScans, Duration window, int maxEntries, Duration idleExpiration) {
        return new AnonymousTrialProperties(
                true, maxScans, window, new AnonymousTrialProperties.Store(maxEntries, idleExpiration));
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
}
