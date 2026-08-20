package com.lyanhkhoa.linksentry.license.application;

import com.lyanhkhoa.linksentry.common.config.LicenseProperties;
import com.lyanhkhoa.linksentry.license.domain.DeviceRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes device installations older than {@code linksentry.license.pending-device-retention} that
 * were never granted any license — bounding unbounded growth from the public, unauthenticated
 * {@code POST /api/v1/devices}. A device with any assignment history at all (including a later-revoked
 * one) is never a candidate: only {@link com.lyanhkhoa.linksentry.license.domain.DeviceRepository
 * #deleteNeverAssignedOlderThan} decides eligibility, and it excludes any device that ever appears in
 * {@code device_license_assignment}.
 *
 * <p>Scheduling only, same {@code @EnableScheduling} activation
 * {@code history.application.HistoryConfiguration} already turns on application-wide — no second
 * {@code @EnableScheduling} is needed here.
 */
@Service
public class DevicePendingRetentionService {

    private final DeviceRepository repository;
    private final LicenseProperties licenseProperties;
    private final Clock clock;

    public DevicePendingRetentionService(DeviceRepository repository, LicenseProperties licenseProperties, Clock clock) {
        this.repository = repository;
        this.licenseProperties = licenseProperties;
        this.clock = clock;
    }

    /**
     * Scheduled hourly in UTC, offset 30 minutes from the scan-history retention job so the two never
     * contend for the same tick. The public method is also directly callable from tests, so retention
     * behavior never depends on waiting for a scheduler tick.
     */
    @Scheduled(cron = "0 30 * * * *", zone = "UTC")
    @Transactional
    public void purgeNeverAssigned() {
        repository.deleteNeverAssignedOlderThan(retentionCutoff());
    }

    private Instant retentionCutoff() {
        return Instant.now(clock).minus(licenseProperties.pendingDeviceRetention());
    }
}
