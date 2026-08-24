package com.lyanhkhoa.linksentry.history.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /** One row containing all scan-level insight counts. */
    interface ScanHistoryInsightsProjection {
        long getTotalScans();

        long getScansLast7Days();

        long getPrevious7Days();

        long getLowCount();

        long getModerateCount();

        long getHighCount();

        long getCriticalCount();
    }

    /** Grouped finding count; no finding copy or evidence is selected. */
    interface TopFindingCountProjection {
        String getRuleId();

        long getFindingCount();
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
              and (:riskLevel is null or scan.risk_level = :riskLevel)
              and (
                    :ruleId is null
                    or exists (
                        select 1
                        from scan_history_finding filter_finding
                        where filter_finding.scan_id = scan.scan_id
                          and filter_finding.rule_id = :ruleId
                    )
              )
            order by scan.analyzed_at desc, scan.scan_id desc
            limit :maxResults
            """, nativeQuery = true)
    List<ScanHistorySummaryProjection> findRetainedFirstPage(
            @Param("ownerLicenseId") UUID ownerLicenseId,
            @Param("retainedSince") Instant retainedSince,
            @Param("riskLevel") String riskLevel,
            @Param("ruleId") String ruleId,
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
              and (:riskLevel is null or scan.risk_level = :riskLevel)
              and (
                    :ruleId is null
                    or exists (
                        select 1
                        from scan_history_finding filter_finding
                        where filter_finding.scan_id = scan.scan_id
                          and filter_finding.rule_id = :ruleId
                    )
              )
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
            @Param("riskLevel") String riskLevel,
            @Param("ruleId") String ruleId,
            @Param("cursorAnalyzedAt") Instant cursorAnalyzedAt,
            @Param("cursorScanId") UUID cursorScanId,
            @Param("maxResults") int maxResults);

    @Query(value = """
            select
                count(*) filter (where scan.analyzed_at >= :windowSince) as "totalScans",
                count(*) filter (where scan.analyzed_at >= :last7DaysSince) as "scansLast7Days",
                count(*) filter (
                    where scan.analyzed_at >= :previous7DaysSince
                      and scan.analyzed_at < :last7DaysSince
                ) as "previous7Days",
                count(*) filter (where scan.analyzed_at >= :windowSince and scan.risk_level = 'LOW')
                    as "lowCount",
                count(*) filter (where scan.analyzed_at >= :windowSince and scan.risk_level = 'MODERATE')
                    as "moderateCount",
                count(*) filter (where scan.analyzed_at >= :windowSince and scan.risk_level = 'HIGH')
                    as "highCount",
                count(*) filter (where scan.analyzed_at >= :windowSince and scan.risk_level = 'CRITICAL')
                    as "criticalCount"
            from scan_history scan
            where scan.owner_license_id = :ownerLicenseId
              and scan.analyzed_at >= :retainedSince
              and scan.analyzed_at <= :now
            """, nativeQuery = true)
    ScanHistoryInsightsProjection findRetainedInsights(
            @Param("ownerLicenseId") UUID ownerLicenseId,
            @Param("retainedSince") Instant retainedSince,
            @Param("now") Instant now,
            @Param("windowSince") Instant windowSince,
            @Param("last7DaysSince") Instant last7DaysSince,
            @Param("previous7DaysSince") Instant previous7DaysSince);

    @Query(value = """
            select finding.rule_id as "ruleId", count(*) as "findingCount"
            from scan_history scan
            join scan_history_finding finding on finding.scan_id = scan.scan_id
            where scan.owner_license_id = :ownerLicenseId
              and scan.analyzed_at >= :retainedSince
              and scan.analyzed_at <= :now
              and scan.analyzed_at >= :windowSince
              and finding.rule_id in (:serverRuleIds)
            group by finding.rule_id
            order by count(*) desc, finding.rule_id asc
            limit :maxResults
            """, nativeQuery = true)
    List<TopFindingCountProjection> findTopFindingCounts(
            @Param("ownerLicenseId") UUID ownerLicenseId,
            @Param("retainedSince") Instant retainedSince,
            @Param("now") Instant now,
            @Param("windowSince") Instant windowSince,
            @Param("serverRuleIds") Set<String> serverRuleIds,
            @Param("maxResults") int maxResults);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ScanHistoryEntity scan where scan.analyzedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
