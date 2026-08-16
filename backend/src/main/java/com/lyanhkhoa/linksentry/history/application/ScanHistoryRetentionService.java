package com.lyanhkhoa.linksentry.history.application;

import com.lyanhkhoa.linksentry.common.config.HistoryProperties;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Removes snapshots older than the configured retention window. */
@Service
public class ScanHistoryRetentionService {

    private final ScanHistoryRepository repository;
    private final HistoryProperties historyProperties;
    private final Clock clock;

    public ScanHistoryRetentionService(
            ScanHistoryRepository repository, HistoryProperties historyProperties, Clock clock) {
        this.repository = repository;
        this.historyProperties = historyProperties;
        this.clock = clock;
    }

    /**
     * Scheduled hourly in UTC. The public method is also directly callable from
     * tests, so retention behavior never depends on waiting for a scheduler tick.
     */
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    @Transactional
    public void purgeExpired() {
        repository.deleteOlderThan(retentionCutoff());
    }

    private Instant retentionCutoff() {
        return Instant.now(clock).minus(historyProperties.retentionDays(), ChronoUnit.DAYS);
    }
}
