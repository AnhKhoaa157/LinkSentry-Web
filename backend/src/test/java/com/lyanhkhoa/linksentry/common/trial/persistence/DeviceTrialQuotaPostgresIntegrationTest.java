package com.lyanhkhoa.linksentry.common.trial.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.LinkSentryApplication;
import com.lyanhkhoa.linksentry.license.application.DevicePendingRetentionService;
import com.lyanhkhoa.linksentry.license.application.LicenseAdminService;
import com.lyanhkhoa.linksentry.license.api.CreateLicenseRequest;
import com.lyanhkhoa.linksentry.license.domain.Device;
import com.lyanhkhoa.linksentry.license.domain.DeviceRepository;
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
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the persistence layer ADR 0010 specifies, against real PostgreSQL: the V6 migration's
 * schema/FK/indexes, the exact inclusive-window boundary, same-device concurrency, cross-context
 * ("cross-replica") admission sharing one database, the cascade-on-device-delete, and the stale-
 * event sweep. HTTP-level admission (401/429/503, identity coverage, licensed bypass) is proven by
 * {@code common.trial.AnonymousTrialPostgresIntegrationTest}.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class DeviceTrialQuotaPostgresIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-21T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("linksentry_trial_quota_test")
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
        registry.add("linksentry.anonymous-trial.max-scans", () -> "3");
        registry.add("linksentry.anonymous-trial.window", () -> "24h");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceTrialQuotaService quotaService;

    @Autowired
    private TrialScanEventRetentionService retentionService;

    @Autowired
    private DevicePendingRetentionService devicePendingRetentionService;

    @Autowired
    private LicenseAdminService licenseAdminService;

    @AfterEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM scan_history");
        jdbcTemplate.update("DELETE FROM device_trial_scan_event");
        jdbcTemplate.update("DELETE FROM device_license_assignment");
        jdbcTemplate.update("DELETE FROM license");
        jdbcTemplate.update("DELETE FROM device_installation");
    }

    private Device newDevice() {
        Device device = new Device(
                UUID.randomUUID(), "AAAA-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(),
                UUID.randomUUID().toString(), null, FIXED_NOW);
        deviceRepository.save(device);
        return device;
    }

    @Test
    @DisplayName("V6 created the table with exactly its three columns, both indexes, and the ON DELETE CASCADE FK")
    void migrationCreatesExpectedSchema() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'device_trial_scan_event' ORDER BY column_name",
                String.class);
        assertThat(columns).containsExactly("admitted_at", "device_id", "event_id");

        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'device_trial_scan_event'", String.class);
        assertThat(indexes)
                .contains(
                        "device_trial_scan_event_pkey",
                        "device_trial_scan_event_device_admitted_idx",
                        "device_trial_scan_event_admitted_idx");

        String deleteRule = jdbcTemplate.queryForObject(
                """
                SELECT rc.delete_rule
                FROM information_schema.referential_constraints rc
                JOIN information_schema.table_constraints tc
                  ON rc.constraint_name = tc.constraint_name
                WHERE tc.table_name = 'device_trial_scan_event'
                """,
                String.class);
        assertThat(deleteRule).isEqualTo("CASCADE");
    }

    @Test
    @DisplayName("exactly 3 admissions succeed then a 4th is refused, for one device")
    void exactlyThreeAdmissionsThenRefused() {
        Device device = newDevice();

        assertThat(quotaService.tryAdmit(device.deviceId(), FIXED_NOW)).isTrue();
        assertThat(quotaService.tryAdmit(device.deviceId(), FIXED_NOW)).isTrue();
        assertThat(quotaService.tryAdmit(device.deviceId(), FIXED_NOW)).isTrue();
        assertThat(quotaService.tryAdmit(device.deviceId(), FIXED_NOW)).isFalse();
    }

    @Test
    @DisplayName("an event exactly `window` old still counts toward the quota (inclusive boundary)")
    void eventExactlyAtBoundaryStillCounts() {
        Device device = newDevice();
        Instant exactlyWindowOld = FIXED_NOW.minus(java.time.Duration.ofHours(24));
        assertThat(quotaService.tryAdmit(device.deviceId(), exactlyWindowOld)).isTrue();
        assertThat(quotaService.tryAdmit(device.deviceId(), exactlyWindowOld)).isTrue();
        assertThat(quotaService.tryAdmit(device.deviceId(), exactlyWindowOld)).isTrue();

        // Now, evaluated at FIXED_NOW: cutoff = FIXED_NOW - 24h = exactlyWindowOld. The three
        // events sit exactly at that cutoff, not strictly older than it, so they must still count
        // and a 4th admission attempt must be refused.
        assertThat(quotaService.tryAdmit(device.deviceId(), FIXED_NOW)).isFalse();
    }

    @Test
    @DisplayName("an event one instant older than the boundary is pruned and no longer counts")
    void eventOlderThanBoundaryIsPruned() {
        Device device = newDevice();
        // PostgreSQL's TIMESTAMPTZ is microsecond-precision, so the margin here must survive that
        // round-trip: one millisecond past the boundary, not one nanosecond.
        Instant justOverWindow = FIXED_NOW.minus(java.time.Duration.ofHours(24)).minusMillis(1);
        assertThat(quotaService.tryAdmit(device.deviceId(), justOverWindow)).isTrue();
        assertThat(quotaService.tryAdmit(device.deviceId(), justOverWindow)).isTrue();
        assertThat(quotaService.tryAdmit(device.deviceId(), justOverWindow)).isTrue();

        // Evaluated at FIXED_NOW: cutoff = FIXED_NOW - 24h, one nanosecond after the three events —
        // all three are strictly older than cutoff and must be pruned, freeing the full quota.
        assertThat(quotaService.tryAdmit(device.deviceId(), FIXED_NOW)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM device_trial_scan_event WHERE device_id = ?",
                        Integer.class,
                        device.deviceId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrent admissions for the same device, real threads and connections, total exactly maxScans")
    void concurrentAdmissionsForSameDeviceAreSerialized() throws Exception {
        Device device = newDevice();
        int attempts = 10;
        int maxScans = 3;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                tasks.add(() -> {
                    ready.countDown();
                    start.await();
                    return quotaService.tryAdmit(device.deviceId(), FIXED_NOW);
                });
            }
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                futures.add(executor.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long admitted = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(15, TimeUnit.SECONDS)) {
                    admitted++;
                }
            }
            assertThat(admitted).isEqualTo(maxScans);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM device_trial_scan_event WHERE device_id = ?",
                            Integer.class,
                            device.deviceId()))
                    .isEqualTo(maxScans);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName(
            "two independent Spring application contexts sharing one PostgreSQL still total exactly maxScans for one device — proves the lock lives in the database, not one JVM's heap")
    void crossContextAdmissionSharesOneDatabase() throws Exception {
        Device device = newDevice();
        // Command-line-style args, not SpringApplicationBuilder.properties(Map): that method adds a
        // low-priority "defaultProperties" source, which application.yml's own defaults (e.g.
        // spring.datasource.password) would still win over.
        String[] args = {
            "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
            "--spring.datasource.driver-class-name=org.postgresql.Driver",
            "--spring.datasource.username=" + POSTGRES.getUsername(),
            "--spring.datasource.password=" + POSTGRES.getPassword(),
            "--spring.flyway.enabled=false",
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--linksentry.anonymous-trial.max-scans=3",
            "--linksentry.anonymous-trial.window=24h",
            "--server.port=0"
        };

        try (ConfigurableApplicationContext contextA = buildContext(args);
                ConfigurableApplicationContext contextB = buildContext(args)) {
            DeviceTrialQuotaService serviceA = contextA.getBean(DeviceTrialQuotaService.class);
            DeviceTrialQuotaService serviceB = contextB.getBean(DeviceTrialQuotaService.class);

            int attemptsPerContext = 5;
            ExecutorService executor = Executors.newFixedThreadPool(attemptsPerContext * 2);
            CountDownLatch ready = new CountDownLatch(attemptsPerContext * 2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Callable<Boolean>> tasks = new ArrayList<>();
                for (int i = 0; i < attemptsPerContext; i++) {
                    tasks.add(() -> {
                        ready.countDown();
                        start.await();
                        return serviceA.tryAdmit(device.deviceId(), FIXED_NOW);
                    });
                    tasks.add(() -> {
                        ready.countDown();
                        start.await();
                        return serviceB.tryAdmit(device.deviceId(), FIXED_NOW);
                    });
                }
                List<Future<Boolean>> futures = new ArrayList<>();
                for (Callable<Boolean> task : tasks) {
                    futures.add(executor.submit(task));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();

                long admitted = 0;
                for (Future<Boolean> future : futures) {
                    if (future.get(15, TimeUnit.SECONDS)) {
                        admitted++;
                    }
                }
                assertThat(admitted).isEqualTo(3);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static ConfigurableApplicationContext buildContext(String[] args) {
        return new SpringApplicationBuilder(LinkSentryApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @Test
    @DisplayName("deleting a device row cascades to its trial-scan-event rows")
    void deletingDeviceCascadesTrialEvents() {
        Device device = newDevice();
        quotaService.tryAdmit(device.deviceId(), FIXED_NOW);
        quotaService.tryAdmit(device.deviceId(), FIXED_NOW);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM device_trial_scan_event WHERE device_id = ?",
                        Integer.class,
                        device.deviceId()))
                .isEqualTo(2);

        jdbcTemplate.update("DELETE FROM device_installation WHERE device_id = ?", device.deviceId());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM device_trial_scan_event WHERE device_id = ?",
                        Integer.class,
                        device.deviceId()))
                .isZero();
    }

    @Test
    @DisplayName(
            "a never-assigned device's exhausted quota is deleted, device and events together, by the existing pending-device retention purge")
    void neverAssignedDeviceRetentionCascadesQuotaEvents() {
        Instant old = FIXED_NOW.minus(31, ChronoUnit.DAYS);
        Device device = new Device(UUID.randomUUID(), "OLD1-EVNT", "old-quota-hash", null, old);
        deviceRepository.save(device);
        quotaService.tryAdmit(device.deviceId(), old);
        quotaService.tryAdmit(device.deviceId(), old);
        quotaService.tryAdmit(device.deviceId(), old);

        devicePendingRetentionService.purgeNeverAssigned();

        assertThat(deviceRepository.findById(device.deviceId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM device_trial_scan_event WHERE device_id = ?",
                        Integer.class,
                        device.deviceId()))
                .isZero();
    }

    @Test
    @DisplayName(
            "a device with assignment history keeps its exhausted-quota rows forever unless the stale-event sweep runs")
    void staleEventSweepDeletesEventsForAssignedDeviceUntouchedByPendingRetention() {
        Instant old = FIXED_NOW.minus(400, ChronoUnit.DAYS);
        Device device = new Device(UUID.randomUUID(), "HIST-EVNT", "history-quota-hash", null, old);
        deviceRepository.save(device);
        quotaService.tryAdmit(device.deviceId(), old);
        quotaService.tryAdmit(device.deviceId(), old);
        UUID licenseId = licenseAdminService
                .create(new CreateLicenseRequest("trial-quota-sweep-test", null, null))
                .licenseId();
        licenseAdminService.grantDevice(licenseId, device.activationCode());
        licenseAdminService.revokeDevice(device.deviceId());

        // Pending-device retention must not touch this device: it has assignment history.
        devicePendingRetentionService.purgeNeverAssigned();
        assertThat(deviceRepository.findById(device.deviceId())).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM device_trial_scan_event WHERE device_id = ?",
                        Integer.class,
                        device.deviceId()))
                .isEqualTo(2);

        retentionService.sweepStaleEvents();

        assertThat(deviceRepository.findById(device.deviceId())).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM device_trial_scan_event WHERE device_id = ?",
                        Integer.class,
                        device.deviceId()))
                .isZero();
    }

    @Test
    @DisplayName("a fresh event well inside the window survives the stale-event sweep")
    void freshEventSurvivesStaleSweep() {
        Device device = newDevice();
        quotaService.tryAdmit(device.deviceId(), FIXED_NOW);

        retentionService.sweepStaleEvents();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM device_trial_scan_event WHERE device_id = ?",
                        Integer.class,
                        device.deviceId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the stale-event sweep deletes in more than one batch when the batch size is exceeded")
    void staleSweepDeletesAcrossMultipleBatches() {
        Instant old = FIXED_NOW.minus(400, ChronoUnit.DAYS);
        int eventCount = 520; // exceeds the 500-row batch size by design
        AtomicInteger inserted = new AtomicInteger();
        Device device = newDevice();
        for (int i = 0; i < eventCount; i++) {
            jdbcTemplate.update(
                    "INSERT INTO device_trial_scan_event (event_id, device_id, admitted_at) VALUES (?, ?, ?)",
                    UUID.randomUUID(),
                    device.deviceId(),
                    java.sql.Timestamp.from(old.plusSeconds(inserted.incrementAndGet())));
        }

        retentionService.sweepStaleEvents();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM device_trial_scan_event WHERE device_id = ?",
                        Integer.class,
                        device.deviceId()))
                .isZero();
    }
}
