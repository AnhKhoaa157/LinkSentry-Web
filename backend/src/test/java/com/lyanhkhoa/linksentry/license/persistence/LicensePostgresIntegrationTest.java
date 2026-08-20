package com.lyanhkhoa.linksentry.license.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lyanhkhoa.linksentry.license.api.CreateLicenseRequest;
import com.lyanhkhoa.linksentry.license.api.DeviceBootstrapRequest;
import com.lyanhkhoa.linksentry.license.api.LicenseResponse;
import com.lyanhkhoa.linksentry.license.application.DevicePendingRetentionService;
import com.lyanhkhoa.linksentry.license.application.DeviceLimitExceededException;
import com.lyanhkhoa.linksentry.license.application.DeviceService;
import com.lyanhkhoa.linksentry.license.application.LicenseAdminService;
import com.lyanhkhoa.linksentry.license.domain.Device;
import com.lyanhkhoa.linksentry.license.domain.DeviceLicenseAssignment;
import com.lyanhkhoa.linksentry.license.domain.DeviceLicenseAssignmentRepository;
import com.lyanhkhoa.linksentry.license.domain.DeviceRepository;
import com.lyanhkhoa.linksentry.license.domain.License;
import com.lyanhkhoa.linksentry.license.domain.LicenseRepository;
import com.lyanhkhoa.linksentry.scan.application.ScanService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the V4 migration's schema and constraint behaviour against real PostgreSQL: the partial unique
 * index that allows at most one active assignment per device, and the cascade/set-null foreign-key
 * behaviour that preserves history when a license is revoked-and-later-deleted-in-theory (the application
 * never deletes a license, but the constraint itself must still hold as documented).
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class LicensePostgresIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-20T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("linksentry_license_test")
            .withUsername("linksentry")
            .withPassword("integration-test");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private LicenseRepository licenseRepository;

    @Autowired
    private DeviceLicenseAssignmentRepository assignmentRepository;

    @Autowired
    private LicenseAdminService licenseAdminService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DevicePendingRetentionService retentionService;

    // Unused directly, but forces the full application context (including ScanService's
    // dependency graph) to wire successfully in the same context this test shares.
    @Autowired
    private ScanService scanService;

    @AfterEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM scan_history");
        jdbcTemplate.update("DELETE FROM device_license_assignment");
        jdbcTemplate.update("DELETE FROM license");
        jdbcTemplate.update("DELETE FROM device_installation");
    }

    @Test
    @DisplayName("a device, a license, and an assignment round-trip through the real schema unchanged")
    void roundTripsDeviceLicenseAndAssignment() {
        Device device = new Device(UUID.randomUUID(), "K7H9-QX3P", "credential-hash-value", "web", FIXED_NOW);
        deviceRepository.save(device);
        License license = new License(UUID.randomUUID(), "integration test", null, 2, null, FIXED_NOW);
        licenseRepository.save(license);
        DeviceLicenseAssignment assignment = new DeviceLicenseAssignment(
                UUID.randomUUID(), license.licenseId(), device.deviceId(), FIXED_NOW, null);
        assignmentRepository.save(assignment);

        assertThat(deviceRepository.findByCredentialHash("credential-hash-value")).contains(device);
        assertThat(deviceRepository.findByActivationCode("K7H9-QX3P")).contains(device);
        assertThat(licenseRepository.findById(license.licenseId())).contains(license);
        assertThat(assignmentRepository.findActiveByDeviceId(device.deviceId())).contains(assignment);
        assertThat(assignmentRepository.countActiveByLicenseId(license.licenseId())).isEqualTo(1);
    }

    @Test
    @DisplayName("the database rejects a second simultaneously active assignment for the same device")
    void databaseRejectsTwoActiveAssignmentsForSameDevice() {
        Device device = new Device(UUID.randomUUID(), "AAAA-BBBB", "hash-1", null, FIXED_NOW);
        deviceRepository.save(device);
        License firstLicense = new License(UUID.randomUUID(), "first", null, 2, null, FIXED_NOW);
        License secondLicense = new License(UUID.randomUUID(), "second", null, 2, null, FIXED_NOW);
        licenseRepository.save(firstLicense);
        licenseRepository.save(secondLicense);
        assignmentRepository.save(
                new DeviceLicenseAssignment(UUID.randomUUID(), firstLicense.licenseId(), device.deviceId(), FIXED_NOW, null));

        assertThatThrownBy(() -> assignmentRepository.save(new DeviceLicenseAssignment(
                        UUID.randomUUID(), secondLicense.licenseId(), device.deviceId(), FIXED_NOW, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("revoking and re-granting a device is allowed: the partial unique index only ever blocks two simultaneously-active rows")
    void revokeThenRegrantIsAllowed() {
        Device device = new Device(UUID.randomUUID(), "CCCC-DDDD", "hash-2", null, FIXED_NOW);
        deviceRepository.save(device);
        License license = new License(UUID.randomUUID(), "label", null, 2, null, FIXED_NOW);
        licenseRepository.save(license);
        DeviceLicenseAssignment first = new DeviceLicenseAssignment(
                UUID.randomUUID(), license.licenseId(), device.deviceId(), FIXED_NOW, null);
        assignmentRepository.save(first);
        assignmentRepository.revoke(first.assignmentId(), FIXED_NOW.plusSeconds(60));

        assignmentRepository.save(new DeviceLicenseAssignment(
                UUID.randomUUID(), license.licenseId(), device.deviceId(), FIXED_NOW.plusSeconds(120), null));

        assertThat(assignmentRepository.findActiveByDeviceId(device.deviceId())).isPresent();
        assertThat(assignmentRepository.findLatestByDeviceId(device.deviceId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    @DisplayName("activation codes and credential hashes are unique across devices")
    void activationCodeAndCredentialHashAreUnique() {
        deviceRepository.save(new Device(UUID.randomUUID(), "DUP-CODE", "dup-hash", null, FIXED_NOW));

        assertThatThrownBy(() -> deviceRepository.save(
                        new Device(UUID.randomUUID(), "DUP-CODE", "different-hash", null, FIXED_NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("DELETE FROM device_installation");
        deviceRepository.save(new Device(UUID.randomUUID(), "ANOTHER-CODE", "dup-hash-2", null, FIXED_NOW));
        assertThatThrownBy(() -> deviceRepository.save(
                        new Device(UUID.randomUUID(), "YET-ANOTHER-CODE", "dup-hash-2", null, FIXED_NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("deleting a license cascades to its assignments but only sets scan_history.owner_license_id null")
    void deletingLicenseCascadesAssignmentsAndOrphansHistory() {
        Device device = new Device(UUID.randomUUID(), "EEEE-FFFF", "hash-3", null, FIXED_NOW);
        deviceRepository.save(device);
        License license = new License(UUID.randomUUID(), "label", null, 2, null, FIXED_NOW);
        licenseRepository.save(license);
        assignmentRepository.save(
                new DeviceLicenseAssignment(UUID.randomUUID(), license.licenseId(), device.deviceId(), FIXED_NOW, null));
        UUID scanId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO scan_history
                    (scan_id, redacted_display_value, scheme, host, ascii_host, registrable_domain, path,
                     query_present, fragment_present, score, risk_level, engine_version, analyzed_at, owner_license_id)
                VALUES (?, 'https://example.com/', 'https', 'example.com', 'example.com', 'example.com', '/',
                        false, false, 0, 'LOW', '0.1.0', ?, ?)
                """,
                scanId, java.sql.Timestamp.from(FIXED_NOW), license.licenseId());

        jdbcTemplate.update("DELETE FROM license WHERE license_id = ?", license.licenseId());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM device_license_assignment WHERE license_id = ?", Integer.class, license.licenseId()))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM scan_history WHERE scan_id = ?", Integer.class, scanId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT owner_license_id FROM scan_history WHERE scan_id = ?", UUID.class, scanId))
                .isNull();
    }

    @Test
    @DisplayName("concurrent grant requests for the same license never oversubscribe maxDevices, even racing")
    void concurrentGrantsNeverExceedDeviceCap() throws Exception {
        int maxDevices = 2;
        int concurrentRequests = 6;
        UUID licenseId = licenseAdminService.create(new CreateLicenseRequest("concurrency-test", null, maxDevices))
                .licenseId();
        List<String> activationCodes = new ArrayList<>();
        for (int i = 0; i < concurrentRequests; i++) {
            activationCodes.add(deviceService.bootstrap(new DeviceBootstrapRequest("device-" + i)).activationCode());
        }

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger limitExceededCount = new AtomicInteger();
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (String code : activationCodes) {
                tasks.add(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        licenseAdminService.grantDevice(licenseId, code);
                        return true;
                    } catch (DeviceLimitExceededException exception) {
                        limitExceededCount.incrementAndGet();
                        return false;
                    }
                });
            }
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                futures.add(executor.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long succeeded = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(15, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }

            // The cap, not "some" and not "all": every one of the extra requests must have
            // been rejected, never silently dropped or double-counted.
            assertThat(succeeded).isEqualTo(maxDevices);
            assertThat(limitExceededCount.get()).isEqualTo(concurrentRequests - maxDevices);
            assertThat(assignmentRepository.countActiveByLicenseId(licenseId)).isEqualTo(maxDevices);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM device_license_assignment WHERE license_id = ? AND revoked_at IS NULL",
                            Integer.class,
                            licenseId))
                    .isEqualTo(maxDevices);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("a pending device younger than the retention window survives purge")
    void freshPendingDeviceSurvivesPurge() {
        Device device = new Device(UUID.randomUUID(), "FRESH-CODE", "fresh-hash", null, FIXED_NOW);
        deviceRepository.save(device);

        retentionService.purgeNeverAssigned();

        assertThat(deviceRepository.findById(device.deviceId())).isPresent();
    }

    @Test
    @DisplayName("a pending device older than the retention window is deleted by purge")
    void oldPendingDeviceIsDeletedByPurge() {
        Instant old = FIXED_NOW.minus(31, ChronoUnit.DAYS);
        Device device = new Device(UUID.randomUUID(), "OLD-CODE", "old-hash", null, old);
        deviceRepository.save(device);

        retentionService.purgeNeverAssigned();

        assertThat(deviceRepository.findById(device.deviceId())).isEmpty();
    }

    @Test
    @DisplayName("a device with only a revoked assignment is never purged, no matter its age")
    void deviceWithRevokedAssignmentSurvivesPurgeRegardlessOfAge() {
        Instant old = FIXED_NOW.minus(365, ChronoUnit.DAYS);
        Device device = new Device(UUID.randomUUID(), "REVOKED-HISTORY", "revoked-hash", null, old);
        deviceRepository.save(device);
        License license = new License(UUID.randomUUID(), "label", null, 2, null, FIXED_NOW);
        licenseRepository.save(license);
        DeviceLicenseAssignment assignment =
                new DeviceLicenseAssignment(UUID.randomUUID(), license.licenseId(), device.deviceId(), old, null);
        assignmentRepository.save(assignment);
        assignmentRepository.revoke(assignment.assignmentId(), old.plusSeconds(60));

        retentionService.purgeNeverAssigned();

        assertThat(deviceRepository.findById(device.deviceId())).isPresent();
    }

    @Test
    @DisplayName("a currently licensed device is never purged, no matter its age")
    void currentlyLicensedDeviceSurvivesPurgeRegardlessOfAge() {
        Instant old = FIXED_NOW.minus(365, ChronoUnit.DAYS);
        Device device = new Device(UUID.randomUUID(), "LICENSED-OLD", "licensed-hash", null, old);
        deviceRepository.save(device);
        License license = new License(UUID.randomUUID(), "label", null, 2, null, FIXED_NOW);
        licenseRepository.save(license);
        assignmentRepository.save(
                new DeviceLicenseAssignment(UUID.randomUUID(), license.licenseId(), device.deviceId(), old, null));

        retentionService.purgeNeverAssigned();

        assertThat(deviceRepository.findById(device.deviceId())).isPresent();
    }

    @Test
    @DisplayName("the activation-code grant flow still works for a pending device inside the retention window, even right after a purge sweep")
    void activationCodeFlowWorksWithinRetentionWindow() {
        Device device = new Device(
                UUID.randomUUID(), "WITHIN-WINDOW", "within-hash", null, FIXED_NOW.minus(29, ChronoUnit.DAYS));
        deviceRepository.save(device);
        License license = new License(UUID.randomUUID(), "label", null, 2, null, FIXED_NOW);
        licenseRepository.save(license);

        retentionService.purgeNeverAssigned();
        LicenseResponse response = licenseAdminService.grantDevice(license.licenseId(), "WITHIN-WINDOW");

        assertThat(response.devices()).hasSize(1);
        assertThat(response.devices().get(0).deviceId()).isEqualTo(device.deviceId());
    }
}
