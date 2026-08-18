package com.lyanhkhoa.linksentry.history.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.common.config.HistoryProperties;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryRepository;
import com.lyanhkhoa.linksentry.history.domain.StoredNormalizedUrl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScanHistoryServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-16T12:00:00Z");
    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("save delegates to the repository exactly once with the same snapshot")
    void saveDelegatesToRepositoryOnce() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        ScanHistoryService service = new ScanHistoryService(repository, new HistoryProperties(30), fixedClock);
        ScanHistory scanHistory = sampleHistory(FIXED_NOW);

        service.save(scanHistory);

        verify(repository).save(scanHistory);
    }

    @Test
    @DisplayName("findRetained computes the retention cutoff from the injected clock, not wall-clock time")
    void findRetainedUsesInjectedClockToComputeCutoff() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        UUID scanId = UUID.randomUUID();
        when(repository.findRetained(eq(scanId), eq(OWNER_ID), any(Instant.class))).thenReturn(Optional.empty());
        ScanHistoryService service = new ScanHistoryService(repository, new HistoryProperties(30), fixedClock);

        service.findRetained(scanId, OWNER_ID);

        // 30 days before the fixed clock's instant, computed deterministically.
        verify(repository).findRetained(scanId, OWNER_ID, Instant.parse("2026-07-17T12:00:00Z"));
    }

    @Test
    @DisplayName("findRetained honors a differently configured retention window")
    void findRetainedHonorsConfiguredRetentionDays() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        UUID scanId = UUID.randomUUID();
        when(repository.findRetained(eq(scanId), eq(OWNER_ID), any(Instant.class))).thenReturn(Optional.empty());
        ScanHistoryService service = new ScanHistoryService(repository, new HistoryProperties(7), fixedClock);

        service.findRetained(scanId, OWNER_ID);

        verify(repository).findRetained(scanId, OWNER_ID, Instant.parse("2026-08-09T12:00:00Z"));
    }

    @Test
    @DisplayName("findRetained returns empty, without throwing, when the repository has no match")
    void findRetainedReturnsEmptyWhenRepositoryHasNoMatch() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        UUID scanId = UUID.randomUUID();
        when(repository.findRetained(eq(scanId), eq(OWNER_ID), any(Instant.class))).thenReturn(Optional.empty());
        ScanHistoryService service = new ScanHistoryService(repository, new HistoryProperties(30), fixedClock);

        assertThat(service.findRetained(scanId, OWNER_ID)).isEmpty();
    }

    private ScanHistory sampleHistory(Instant analyzedAt) {
        StoredNormalizedUrl normalized = new StoredNormalizedUrl(
                "https", "example.com", "example.com", "example.com", null, "/", true, false);
        return new ScanHistory(
                UUID.randomUUID(), "https://example.com/", normalized, 0, RiskLevel.LOW, List.of(), "0.1.0",
                analyzedAt, OWNER_ID);
    }
}
