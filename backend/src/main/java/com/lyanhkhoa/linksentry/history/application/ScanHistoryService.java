package com.lyanhkhoa.linksentry.history.application;

import com.lyanhkhoa.linksentry.common.config.HistoryProperties;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for writing and reading retained scan snapshots. */
@Service
public class ScanHistoryService {

    private final ScanHistoryRepository repository;
    private final HistoryProperties historyProperties;
    private final Clock clock;

    public ScanHistoryService(
            ScanHistoryRepository repository, HistoryProperties historyProperties, Clock clock) {
        this.repository = repository;
        this.historyProperties = historyProperties;
        this.clock = clock;
    }

    /** Persists one completed scan; callers invoke this only after analysis succeeds. */
    @Transactional
    public void save(ScanHistory scanHistory) {
        Objects.requireNonNull(scanHistory.ownerUserId(), "ownerUserId");
        repository.save(scanHistory);
    }

    /** Returns a scan only when it has not crossed the configured retention boundary. */
    @Transactional(readOnly = true)
    public Optional<ScanHistory> findRetained(UUID scanId, UUID ownerUserId) {
        if (ownerUserId == null) {
            return Optional.empty();
        }
        return repository.findRetained(scanId, ownerUserId, retainedSince());
    }

    private Instant retainedSince() {
        return Instant.now(clock).minus(historyProperties.retentionDays(), ChronoUnit.DAYS);
    }
}
