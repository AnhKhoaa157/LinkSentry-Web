package com.lyanhkhoa.linksentry.common.trial;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * One in-process rolling-window trial counter per server-observed remote address,
 * bounded the same way as {@code common.ratelimit.RateLimitBucketStore}: a hard cap
 * evicts the least-recently-used entry the moment {@code store.max-entries} would be
 * exceeded, and a scheduled sweep removes entries idle past
 * {@code store.idle-expiration}. State lives only in this instance's heap and resets
 * on restart — see {@code docs/ARCHITECTURE.md} for the single-instance caveat this
 * implies.
 *
 * <p>Each entry holds the timestamps of its accepted scans, oldest first. Because
 * {@code maxScans} is small and every accepted timestamp is appended in order,
 * pruning is a cheap scan from the front of the deque rather than a token-bucket
 * approximation — this is an actual rolling window, not a refill rate.
 */
@Component
public class AnonymousTrialStore {

    private final AnonymousTrialProperties properties;
    private final Clock clock;
    private final Object lock = new Object();
    private final LinkedHashMap<String, Entry> entries;

    public AnonymousTrialStore(AnonymousTrialProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        int maxEntries = properties.store().maxEntries();
        // accessOrder=true turns get() into an LRU touch; removeEldestEntry() then
        // fires from put() the moment a new key would push the map past maxEntries.
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /**
     * Attempts to record one more accepted anonymous scan for {@code remoteAddr}.
     *
     * <p>The window boundary is inclusive: a scan ages out only once it is strictly
     * older than {@code window}, not the instant it turns exactly {@code window} old.
     *
     * @return {@code true} and records the attempt when {@code remoteAddr} is still
     *     under {@code maxScans} within the rolling {@code window}; {@code false},
     *     recording nothing, once the window's quota is exhausted
     */
    boolean tryConsume(String remoteAddr) {
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(properties.window());
        synchronized (lock) {
            Entry entry = entries.get(remoteAddr);
            if (entry == null) {
                entry = new Entry();
                entries.put(remoteAddr, entry);
            }
            entry.lastAccess = now;
            pruneExpired(entry, cutoff);
            if (entry.timestamps.size() >= properties.maxScans()) {
                return false;
            }
            entry.timestamps.addLast(now);
            return true;
        }
    }

    private static void pruneExpired(Entry entry, Instant cutoff) {
        while (!entry.timestamps.isEmpty() && entry.timestamps.peekFirst().isBefore(cutoff)) {
            entry.timestamps.removeFirst();
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

    /** Number of remote addresses currently tracked. Test-only visibility hook. */
    int trackedIdentityCount() {
        synchronized (lock) {
            return entries.size();
        }
    }

    private static final class Entry {
        private final Deque<Instant> timestamps = new ArrayDeque<>();
        private volatile Instant lastAccess;
    }
}
