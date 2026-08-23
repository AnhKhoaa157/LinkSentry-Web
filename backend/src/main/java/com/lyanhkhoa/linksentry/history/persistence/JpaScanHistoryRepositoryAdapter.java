package com.lyanhkhoa.linksentry.history.persistence;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryCursor;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryRepository;
import com.lyanhkhoa.linksentry.history.domain.ScanHistorySummary;
import com.lyanhkhoa.linksentry.history.domain.StoredFinding;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
            Optional<ScanHistoryCursor> cursor,
            int maxResults) {
        List<SpringDataScanHistoryRepository.ScanHistorySummaryProjection> rows = cursor
                .map(position -> repository.findRetainedPageAfter(
                        ownerLicenseId,
                        retainedSince,
                        position.analyzedAt(),
                        position.scanId(),
                        maxResults))
                .orElseGet(() -> repository.findRetainedFirstPage(ownerLicenseId, retainedSince, maxResults));
        return rows.stream().map(JpaScanHistoryRepositoryAdapter::toSummary).toList();
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
