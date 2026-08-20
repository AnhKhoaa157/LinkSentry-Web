package com.lyanhkhoa.linksentry.history.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.history.application.ScanHistoryRetentionService;
import com.lyanhkhoa.linksentry.history.application.ScanHistoryService;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.StoredNormalizedUrl;
import com.lyanhkhoa.linksentry.license.domain.License;
import com.lyanhkhoa.linksentry.license.domain.LicenseRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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
 * Verifies the retention boundary against real PostgreSQL comparison semantics, with a fixed
 * {@link Clock} so the cutoff instant is exact rather than a moving target.
 *
 * <p>Kept separate from {@link ScanHistoryPostgresIntegrationTest} so overriding the {@code
 * Clock} bean here cannot affect that class's own wall-clock-based expiry assertions.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class ScanHistoryRetentionBoundaryPostgresTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-16T12:00:00Z");
    private static final int RETENTION_DAYS = 30;
    private static final Instant CUTOFF = FIXED_NOW.minus(RETENTION_DAYS, ChronoUnit.DAYS);
    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("linksentry_retention_boundary_test")
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
        registry.add("linksentry.history.retention-days", () -> RETENTION_DAYS);
    }

    @Autowired
    private ScanHistoryService historyService;

    @Autowired
    private ScanHistoryRetentionService retentionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LicenseRepository licenseRepository;

    @AfterEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM scan_history");
        jdbcTemplate.update("DELETE FROM device_license_assignment");
        jdbcTemplate.update("DELETE FROM license");
        jdbcTemplate.update("DELETE FROM device_installation");
    }

    @BeforeEach
    void createOwner() {
        licenseRepository.save(new License(OWNER_ID, "retention-test", null, 2, null, FIXED_NOW));
    }

    @Test
    @DisplayName("a record exactly at the retention cutoff is retained and survives purge")
    void exactlyAtCutoffIsRetainedAndSurvivesPurge() {
        UUID scanId = UUID.randomUUID();
        historyService.save(snapshot(scanId, CUTOFF));

        assertThat(historyService.findRetained(scanId, OWNER_ID)).isPresent();

        retentionService.purgeExpired();

        assertThat(rowCount(scanId)).isEqualTo(1);
    }

    @Test
    @DisplayName("a record one second before the retention cutoff is excluded from retrieval and purged")
    void justBeforeCutoffIsExcludedAndPurged() {
        UUID scanId = UUID.randomUUID();
        historyService.save(snapshot(scanId, CUTOFF.minusSeconds(1)));

        assertThat(historyService.findRetained(scanId, OWNER_ID)).isEmpty();

        retentionService.purgeExpired();

        assertThat(rowCount(scanId)).isZero();
    }

    @Test
    @DisplayName("a record one second after the retention cutoff is retained and survives purge")
    void justAfterCutoffIsRetainedAndSurvivesPurge() {
        UUID scanId = UUID.randomUUID();
        historyService.save(snapshot(scanId, CUTOFF.plusSeconds(1)));

        assertThat(historyService.findRetained(scanId, OWNER_ID)).isPresent();

        retentionService.purgeExpired();

        assertThat(rowCount(scanId)).isEqualTo(1);
    }

    private Integer rowCount(UUID scanId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scan_history WHERE scan_id = ?", Integer.class, scanId);
    }

    private ScanHistory snapshot(UUID scanId, Instant analyzedAt) {
        StoredNormalizedUrl normalized = new StoredNormalizedUrl(
                "https", "example.com", "example.com", "example.com", null, "/", true, false);
        return new ScanHistory(
                scanId, "https://example.com/", normalized, 0, RiskLevel.LOW, List.of(), "0.1.0", analyzedAt,
                OWNER_ID);
    }
}
