package com.lyanhkhoa.linksentry.history.persistence;

import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryRepository;
import com.lyanhkhoa.linksentry.history.domain.StoredFinding;
import java.time.Instant;
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
    public Optional<ScanHistory> findRetained(UUID scanId, Instant retainedSince) {
        return repository.findRetained(scanId, retainedSince).map(ScanHistoryEntity::toDomain);
    }

    @Override
    public long deleteOlderThan(Instant cutoff) {
        return repository.deleteOlderThan(cutoff);
    }
}
