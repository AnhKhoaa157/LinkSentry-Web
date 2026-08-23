package com.lyanhkhoa.linksentry.history.domain;

import java.time.Instant;
import java.util.List;
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
     * @param ownerLicenseId the scan's owning license; null is never a readable owner
     * @param retainedSince inclusive retention cutoff
     */
    Optional<ScanHistory> findRetained(UUID scanId, UUID ownerLicenseId, Instant retainedSince);

    /**
     * Returns at most {@code maxResults} retained summaries after an optional keyset position.
     * Results are ordered by analyzed time and scan ID, both descending.
     */
    List<ScanHistorySummary> findRetainedPage(
            UUID ownerLicenseId,
            Instant retainedSince,
            Optional<ScanHistoryCursor> cursor,
            int maxResults);

    /** Deletes records strictly older than the supplied retention cutoff. */
    long deleteOlderThan(Instant cutoff);
}
