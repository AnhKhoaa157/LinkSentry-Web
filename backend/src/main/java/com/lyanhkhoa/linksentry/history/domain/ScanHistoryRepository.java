package com.lyanhkhoa.linksentry.history.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for safe scan history snapshots. */
public interface ScanHistoryRepository {

    /** Stores one immutable scan snapshot. */
    void save(ScanHistory scanHistory);

    /**
     * Finds a scan only when it is still within the retention window.
     *
     * @param scanId opaque scan UUID
     * @param retainedSince inclusive retention cutoff
     */
    Optional<ScanHistory> findRetained(UUID scanId, Instant retainedSince);

    /** Deletes records strictly older than the supplied retention cutoff. */
    long deleteOlderThan(Instant cutoff);
}
