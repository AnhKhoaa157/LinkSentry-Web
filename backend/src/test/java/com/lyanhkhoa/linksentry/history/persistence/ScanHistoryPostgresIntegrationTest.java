package com.lyanhkhoa.linksentry.history.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.history.application.ScanHistoryRetentionService;
import com.lyanhkhoa.linksentry.history.application.ScanHistoryService;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.StoredFinding;
import com.lyanhkhoa.linksentry.history.domain.StoredNormalizedUrl;
import com.lyanhkhoa.linksentry.scan.application.ScanService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies the shipped Flyway schema and safe history mapping against PostgreSQL. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScanHistoryPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("linksentry_history_test")
            .withUsername("linksentry")
            .withPassword("integration-test");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScanHistoryService historyService;

    @Autowired
    private ScanHistoryRetentionService retentionService;

    @Autowired
    private ScanService scanService;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("linksentry.history.retention-days", () -> 30);
    }

    @AfterEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM scan_history");
    }

    @Test
    @DisplayName("Flyway applies the history migration to an empty PostgreSQL database")
    void migrationAppliesToEmptyDatabase() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.scan_history')", String.class))
                .isEqualTo("scan_history");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.scan_history_finding')", String.class))
                .isEqualTo("scan_history_finding");
    }

    @Test
    @DisplayName("save and retrieval preserve every public field and finding order")
    void roundTripsSafeSnapshotAndFindingOrder() {
        UUID scanId = UUID.fromString("2ce16fb9-d52d-4310-8d45-a4e48f31889e");
        ScanHistory original = snapshot(scanId, Instant.parse("2026-08-16T12:00:00Z"));

        historyService.save(original);

        assertThat(historyService.findRetained(scanId)).contains(original);
    }

    @Test
    @DisplayName("raw query, fragment, and credential values never enter stored columns")
    void sensitiveUrlPartsNeverPersist() {
        String querySecret = "query-secret-123";
        String fragmentSecret = "fragment-secret-456";
        String rawUrl = "https://example.com/account?token=" + querySecret + "#" + fragmentSecret;
        int rowsBeforeScan = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scan_history", Integer.class);

        var response = scanService.scan(rawUrl);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scan_history", Integer.class))
                .isEqualTo(rowsBeforeScan + 1);
        assertThat(response.data().input()).isEqualTo("https://example.com/account");
        String storedText = storedTextFor(response.data().scanId());
        assertThat(storedText).contains("https://example.com/account", "example.com");
        assertThat(storedText).doesNotContain(querySecret, fragmentSecret);

        int rowsBeforeCredentialAttempt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scan_history", Integer.class);
        assertThatThrownBy(() -> scanService.scan(
                "https://user:password-secret@example.com/account?token=" + querySecret))
                .isInstanceOf(InvalidUrlException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scan_history", Integer.class))
                .isEqualTo(rowsBeforeCredentialAttempt);
        assertThat(storedTextFor(response.data().scanId())).doesNotContain("password-secret");
    }

    @Test
    @DisplayName("expired records are unavailable and removed by the retention service")
    void expiredRecordsAreUnavailableAndPurged() {
        UUID scanId = UUID.fromString("9f3d7a0c-421f-4d38-bc5d-5a57f2d4f3c1");
        historyService.save(snapshot(scanId, Instant.now().minusSeconds(31L * 24 * 60 * 60)));

        assertThat(historyService.findRetained(scanId)).isEmpty();

        retentionService.purgeExpired();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scan_history WHERE scan_id = ?", Integer.class, scanId))
                .isZero();
    }

    @Test
    @DisplayName("missing and malformed IDs return the documented safe 404")
    void missingAndMalformedIdsReturnSafeNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/scans/{scanId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested scan could not be found."));

        mockMvc.perform(get("/api/v1/scans/not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested scan could not be found."));
    }

    private ScanHistory snapshot(UUID scanId, Instant analyzedAt) {
        StoredNormalizedUrl normalized = new StoredNormalizedUrl(
                "https",
                "login.example.com",
                "login.example.com",
                "example.com",
                443,
                "/Account/Reset",
                true,
                true);
        List<StoredFinding> findings = List.of(
                new StoredFinding(
                        "Z_RULE", com.lyanhkhoa.linksentry.analysis.domain.Severity.LOW, 5,
                        "Later rule", "The later rule explanation.", "safe evidence"),
                new StoredFinding(
                        "A_RULE", com.lyanhkhoa.linksentry.analysis.domain.Severity.HIGH, 35,
                        "Earlier rule", "The earlier rule explanation.", null));
        return new ScanHistory(
                scanId,
                "https://login.example.com:443/Account/Reset",
                normalized,
                40,
                com.lyanhkhoa.linksentry.analysis.domain.RiskLevel.HIGH,
                findings,
                "0.1.0",
                analyzedAt);
    }

    private String storedTextFor(UUID scanId) {
        List<Map<String, Object>> parentRows = jdbcTemplate.queryForList(
                """
                SELECT redacted_display_value, scheme, host, ascii_host, registrable_domain,
                       path, risk_level, engine_version
                FROM scan_history WHERE scan_id = ?
                """,
                scanId);
        List<Map<String, Object>> findingRows = jdbcTemplate.queryForList(
                """
                SELECT rule_id, severity, title, explanation, evidence
                FROM scan_history_finding WHERE scan_id = ? ORDER BY finding_position
                """,
                scanId);
        return Stream.concat(
                        parentRows.stream().flatMap(row -> row.values().stream()),
                        findingRows.stream().flatMap(row -> row.values().stream()))
                .map(String::valueOf)
                .collect(Collectors.joining("|"));
    }
}
