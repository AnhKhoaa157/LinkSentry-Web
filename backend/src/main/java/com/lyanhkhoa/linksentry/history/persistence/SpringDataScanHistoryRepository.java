package com.lyanhkhoa.linksentry.history.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data adapter contract; callers depend on the history domain port instead. */
public interface SpringDataScanHistoryRepository extends JpaRepository<ScanHistoryEntity, UUID> {

    /** Closed projection over the only fields allowed in the history list response. */
    interface ScanHistorySummaryProjection {
        UUID getScanId();

        String getRedactedDisplayValue();

        String getRegistrableDomain();

        int getScore();

        String getRiskLevel();

        long getFindingCount();

        Instant getAnalyzedAt();
    }

    @Query("""
            select scan from ScanHistoryEntity scan
            where scan.scanId = :scanId
              and scan.ownerLicenseId = :ownerLicenseId
              and scan.analyzedAt >= :retainedSince
            """)
    Optional<ScanHistoryEntity> findRetained(
            @Param("scanId") UUID scanId,
            @Param("ownerLicenseId") UUID ownerLicenseId,
            @Param("retainedSince") Instant retainedSince);

    @Query(value = """
            select
                scan.scan_id as "scanId",
                scan.redacted_display_value as "redactedDisplayValue",
                scan.registrable_domain as "registrableDomain",
                scan.score as "score",
                scan.risk_level as "riskLevel",
                (select count(*) from scan_history_finding finding where finding.scan_id = scan.scan_id)
                    as "findingCount",
                scan.analyzed_at as "analyzedAt"
            from scan_history scan
            where scan.owner_license_id = :ownerLicenseId
              and scan.analyzed_at >= :retainedSince
            order by scan.analyzed_at desc, scan.scan_id desc
            limit :maxResults
            """, nativeQuery = true)
    List<ScanHistorySummaryProjection> findRetainedFirstPage(
            @Param("ownerLicenseId") UUID ownerLicenseId,
            @Param("retainedSince") Instant retainedSince,
            @Param("maxResults") int maxResults);

    @Query(value = """
            select
                scan.scan_id as "scanId",
                scan.redacted_display_value as "redactedDisplayValue",
                scan.registrable_domain as "registrableDomain",
                scan.score as "score",
                scan.risk_level as "riskLevel",
                (select count(*) from scan_history_finding finding where finding.scan_id = scan.scan_id)
                    as "findingCount",
                scan.analyzed_at as "analyzedAt"
            from scan_history scan
            where scan.owner_license_id = :ownerLicenseId
              and scan.analyzed_at >= :retainedSince
              and (
                    scan.analyzed_at < :cursorAnalyzedAt
                    or (scan.analyzed_at = :cursorAnalyzedAt and scan.scan_id < :cursorScanId)
              )
            order by scan.analyzed_at desc, scan.scan_id desc
            limit :maxResults
            """, nativeQuery = true)
    List<ScanHistorySummaryProjection> findRetainedPageAfter(
            @Param("ownerLicenseId") UUID ownerLicenseId,
            @Param("retainedSince") Instant retainedSince,
            @Param("cursorAnalyzedAt") Instant cursorAnalyzedAt,
            @Param("cursorScanId") UUID cursorScanId,
            @Param("maxResults") int maxResults);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ScanHistoryEntity scan where scan.analyzedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
