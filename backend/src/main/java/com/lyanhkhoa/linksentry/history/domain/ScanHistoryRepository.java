package com.lyanhkhoa.linksentry.history.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
            ScanHistoryFilter filter,
            Optional<ScanHistoryCursor> cursor,
            int maxResults);

    /** Backward-compatible unfiltered form for callers that do not need M9.3 filters. */
    default List<ScanHistorySummary> findRetainedPage(
            UUID ownerLicenseId,
            Instant retainedSince,
            Optional<ScanHistoryCursor> cursor,
            int maxResults) {
        return findRetainedPage(ownerLicenseId, retainedSince, ScanHistoryFilter.none(), cursor, maxResults);
    }

    /**
     * Returns owner- and retention-scoped aggregate counts without loading finding collections.
     * {@code serverRuleIds} is the bounded backend rule catalog used by the top-findings query.
     */
    ScanHistoryInsightsAggregate findRetainedInsights(
            UUID ownerLicenseId,
            Instant retainedSince,
            Instant now,
            Instant windowSince,
            Instant last7DaysSince,
            Instant previous7DaysSince,
            Set<String> serverRuleIds);

    /** Deletes records strictly older than the supplied retention cutoff. */
    long deleteOlderThan(Instant cutoff);
}
