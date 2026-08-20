package com.lyanhkhoa.linksentry.license.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.lyanhkhoa.linksentry.common.config.LicenseProperties;
import com.lyanhkhoa.linksentry.license.domain.DeviceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DevicePendingRetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    @Test
    void purgesUsingConfiguredRetentionAndInjectedClock() {
        DeviceRepository repository = mock(DeviceRepository.class);
        DevicePendingRetentionService service = new DevicePendingRetentionService(
                repository,
                new LicenseProperties(2, Duration.ofDays(30)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.purgeNeverAssigned();

        verify(repository).deleteNeverAssignedOlderThan(Instant.parse("2026-07-21T12:00:00Z"));
    }

    @Test
    void honorsADifferentlyConfiguredRetentionWindow() {
        DeviceRepository repository = mock(DeviceRepository.class);
        DevicePendingRetentionService service = new DevicePendingRetentionService(
                repository,
                new LicenseProperties(2, Duration.ofHours(6)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.purgeNeverAssigned();

        verify(repository).deleteNeverAssignedOlderThan(Instant.parse("2026-08-20T06:00:00Z"));
    }
}
