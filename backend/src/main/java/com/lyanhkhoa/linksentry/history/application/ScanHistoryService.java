package com.lyanhkhoa.linksentry.history.application;

import com.lyanhkhoa.linksentry.common.config.HistoryProperties;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryCursor;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryRepository;
import com.lyanhkhoa.linksentry.history.domain.ScanHistorySummary;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for writing and reading retained scan snapshots. */
@Service
public class ScanHistoryService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;

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
        Objects.requireNonNull(scanHistory.ownerLicenseId(), "ownerLicenseId");
        repository.save(scanHistory);
    }

    /** Returns a scan only when it has not crossed the configured retention boundary. */
    @Transactional(readOnly = true)
    public Optional<ScanHistory> findRetained(UUID scanId, UUID ownerLicenseId) {
        if (ownerLicenseId == null) {
            return Optional.empty();
        }
        return repository.findRetained(scanId, ownerLicenseId, retainedSince());
    }

    /** Returns one private, retention-aware page for the caller's license. */
    @Transactional(readOnly = true)
    public ScanHistoryPage listRetained(UUID ownerLicenseId, Integer requestedLimit, String rawCursor) {
        int pageSize = requestedLimit == null ? DEFAULT_PAGE_SIZE : requestedLimit;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw InvalidScanHistoryRequestException.invalidLimit();
        }
        if (ownerLicenseId == null) {
            return new ScanHistoryPage(List.of(), null, historyProperties.retentionDays());
        }

        Optional<ScanHistoryCursor> cursor = rawCursor == null
                ? Optional.empty()
                : Optional.of(ScanHistoryCursorCodec.decode(rawCursor));
        List<ScanHistorySummary> candidates = repository.findRetainedPage(
                ownerLicenseId, retainedSince(), cursor, pageSize + 1);
        boolean hasNextPage = candidates.size() > pageSize;
        List<ScanHistorySummary> items = hasNextPage
                ? List.copyOf(candidates.subList(0, pageSize))
                : List.copyOf(candidates);
        String nextCursor = hasNextPage ? cursorFor(items.getLast()) : null;
        return new ScanHistoryPage(items, nextCursor, historyProperties.retentionDays());
    }

    private static String cursorFor(ScanHistorySummary summary) {
        return ScanHistoryCursorCodec.encode(new ScanHistoryCursor(summary.analyzedAt(), summary.scanId()));
    }

    private Instant retainedSince() {
        return Instant.now(clock).minus(historyProperties.retentionDays(), ChronoUnit.DAYS);
    }
}
