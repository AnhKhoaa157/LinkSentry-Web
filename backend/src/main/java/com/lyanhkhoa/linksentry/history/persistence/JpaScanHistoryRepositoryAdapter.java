package com.lyanhkhoa.linksentry.history.persistence;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryCursor;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryFilter;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryFindingCount;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryInsightsAggregate;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryRepository;
import com.lyanhkhoa.linksentry.history.domain.ScanHistorySummary;
import com.lyanhkhoa.linksentry.history.domain.StoredFinding;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/** JPA adapter that keeps persistence details out of the history application layer. */
@Repository
public class JpaScanHistoryRepositoryAdapter implements ScanHistoryRepository {

    private final SpringDataScanHistoryRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaScanHistoryRepositoryAdapter(SpringDataScanHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(ScanHistory scanHistory) {
        ScanHistoryEntity entity = new ScanHistoryEntity(scanHistory);
        for (int position = 0; position < scanHistory.findings().size(); position++) {
            StoredFinding finding = scanHistory.findings().get(position);
            entity.addFinding(position, finding);
        }
        entityManager.persist(entity);
    }

    @Override
    public Optional<ScanHistory> findRetained(UUID scanId, UUID ownerLicenseId, Instant retainedSince) {
        return repository.findRetained(scanId, ownerLicenseId, retainedSince).map(ScanHistoryEntity::toDomain);
    }

    @Override
    public List<ScanHistorySummary> findRetainedPage(
            UUID ownerLicenseId,
            Instant retainedSince,
            ScanHistoryFilter filter,
            Optional<ScanHistoryCursor> cursor,
            int maxResults) {
        String riskLevel = filter.riskLevel().map(Enum::name).orElse(null);
        String ruleId = filter.ruleId().orElse(null);
        List<SpringDataScanHistoryRepository.ScanHistorySummaryProjection> rows = cursor
                .map(position -> repository.findRetainedPageAfter(
                        ownerLicenseId,
                        retainedSince,
                        riskLevel,
                        ruleId,
                        position.analyzedAt(),
                        position.scanId(),
                        maxResults))
                .orElseGet(() -> repository.findRetainedFirstPage(
                        ownerLicenseId, retainedSince, riskLevel, ruleId, maxResults));
        return rows.stream().map(JpaScanHistoryRepositoryAdapter::toSummary).toList();
    }

    @Override
    public ScanHistoryInsightsAggregate findRetainedInsights(
            UUID ownerLicenseId,
            Instant retainedSince,
            Instant now,
            Instant windowSince,
            Instant last7DaysSince,
            Instant previous7DaysSince,
            Set<String> serverRuleIds) {
        SpringDataScanHistoryRepository.ScanHistoryInsightsProjection aggregate =
                repository.findRetainedInsights(
                        ownerLicenseId,
                        retainedSince,
                        now,
                        windowSince,
                        last7DaysSince,
                        previous7DaysSince);
        List<ScanHistoryFindingCount> topFindings = repository.findTopFindingCounts(
                        ownerLicenseId, retainedSince, now, windowSince, serverRuleIds, 5)
                .stream()
                .map(row -> new ScanHistoryFindingCount(row.getRuleId(), row.getFindingCount()))
                .toList();
        return new ScanHistoryInsightsAggregate(
                aggregate.getTotalScans(),
                aggregate.getScansLast7Days(),
                aggregate.getPrevious7Days(),
                java.util.Map.of(
                        RiskLevel.LOW, aggregate.getLowCount(),
                        RiskLevel.MODERATE, aggregate.getModerateCount(),
                        RiskLevel.HIGH, aggregate.getHighCount(),
                        RiskLevel.CRITICAL, aggregate.getCriticalCount()),
                topFindings);
    }

    @Override
    public long deleteOlderThan(Instant cutoff) {
        return repository.deleteOlderThan(cutoff);
    }

    private static ScanHistorySummary toSummary(
            SpringDataScanHistoryRepository.ScanHistorySummaryProjection row) {
        return new ScanHistorySummary(
                row.getScanId(),
                row.getRedactedDisplayValue(),
                row.getRegistrableDomain(),
                row.getScore(),
                RiskLevel.valueOf(row.getRiskLevel()),
                Math.toIntExact(row.getFindingCount()),
                row.getAnalyzedAt());
    }
}
