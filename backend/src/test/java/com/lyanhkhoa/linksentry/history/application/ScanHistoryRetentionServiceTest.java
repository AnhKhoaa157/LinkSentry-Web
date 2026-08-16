package com.lyanhkhoa.linksentry.history.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.lyanhkhoa.linksentry.common.config.HistoryProperties;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ScanHistoryRetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");

    @Test
    void purgesUsingConfiguredRetentionAndInjectedClock() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        ScanHistoryRetentionService service = new ScanHistoryRetentionService(
                repository,
                new HistoryProperties(30),
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.purgeExpired();

        verify(repository).deleteOlderThan(Instant.parse("2026-07-17T12:00:00Z"));
    }
}
