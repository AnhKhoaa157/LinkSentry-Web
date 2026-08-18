package com.lyanhkhoa.linksentry.history.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.history.application.ScanHistoryRetentionService;
import com.lyanhkhoa.linksentry.history.application.ScanHistoryService;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.StoredFinding;
import com.lyanhkhoa.linksentry.history.domain.StoredNormalizedUrl;
import com.lyanhkhoa.linksentry.scan.application.ScanService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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

    // PostgreSQL TIMESTAMPTZ stores microsecond precision and rounds (not truncates) any
    // fractional value finer than a microsecond. A clock reading real wall-clock time can land
    // on a nanosecond value that rounds up on the way into Postgres, so a truncated round-trip
    // comparison can differ by one microsecond depending on timing. Freezing the clock to an
    // instant that is already microsecond-aligned (zero sub-microsecond component) removes the
    // rounding step entirely: there is nothing for Postgres to round, so the stored value is
    // byte-for-byte the value written, and the round-trip assertion can stay exact.
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-17T12:00:00.123456Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("linksentry_history_test")
            .withUsername("linksentry")
            .withPassword("integration-test");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }

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

    @Autowired
    private Clock clock;

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
    @DisplayName("expired records are unavailable and purge cascades to finding rows")
    void expiredRecordsAreUnavailableAndPurged() {
        UUID scanId = UUID.fromString("9f3d7a0c-421f-4d38-bc5d-5a57f2d4f3c1");
        historyService.save(snapshot(scanId, clock.instant().minusSeconds(31L * 24 * 60 * 60)));

        assertThat(historyService.findRetained(scanId)).isEmpty();
        // The parent row exists but is outside the retention window; the two findings from
        // snapshot() must actually be present before purge, or the post-purge zero below would
        // be a false positive (never inserted, rather than deleted by the cascade).
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scan_history_finding WHERE scan_id = ?", Integer.class, scanId))
                .isEqualTo(2);

        retentionService.purgeExpired();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scan_history WHERE scan_id = ?", Integer.class, scanId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scan_history_finding WHERE scan_id = ?", Integer.class, scanId))
                .isZero();
    }

    @Test
    @DisplayName("missing, malformed, and expired IDs all return an identical safe 404")
    void missingMalformedAndExpiredIdsReturnIdenticalSafeNotFound() throws Exception {
        UUID expiredScanId = UUID.fromString("5b1e9c3a-6f2d-4a7b-9e3c-2d1f8a6b4c0e");
        historyService.save(snapshot(expiredScanId, clock.instant().minusSeconds(31L * 24 * 60 * 60)));

        mockMvc.perform(get("/api/v1/scans/{scanId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested scan could not be found."));

        mockMvc.perform(get("/api/v1/scans/not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested scan could not be found."));

        // A record that genuinely existed but expired must be indistinguishable from one that
        // never existed at all: same status, same code, same message.
        mockMvc.perform(get("/api/v1/scans/{scanId}", expiredScanId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested scan could not be found."));
    }

    @Test
    @DisplayName("POST then GET round-trip every public response field through the real schema")
    void postThenGetRoundTripsEveryResponseField() throws Exception {
        String querySecret = "roundtrip-query-secret";
        String fragmentSecret = "roundtrip-fragment-secret";
        // Host mirrors PublicSuffixDomainResolverTest.keepsDeceptiveBrandLabelsAsSubdomains(),
        // a verified case with 4 subdomains under a real public suffix (evil-domain.xyz) —
        // deterministically over the default max-depth of 3, so EXCESSIVE_SUBDOMAINS fires
        // alongside MISSING_HTTPS (http scheme) without depending on an unverified TLD.
        String requestBody = "{\"url\":\"http://login.vietcombank.com.vn.evil-domain.xyz/account?token="
                + querySecret + "#" + fragmentSecret + "\"}";

        MvcResult postResult = mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();
        String postBody = postResult.getResponse().getContentAsString();
        DocumentContext post = JsonPath.parse(postBody);
        String scanId = post.read("$.data.scanId", String.class);

        MvcResult getResult = mockMvc.perform(get("/api/v1/scans/{scanId}", scanId))
                .andExpect(status().isOk())
                .andReturn();
        String getBody = getResult.getResponse().getContentAsString();
        DocumentContext get = JsonPath.parse(getBody);

        assertThat(get.read("$.data.scanId", String.class)).isEqualTo(scanId);
        assertThat(get.read("$.data.input", String.class)).isEqualTo(post.read("$.data.input", String.class));
        assertThat(get.<Object>read("$.data.normalized")).isEqualTo(post.<Object>read("$.data.normalized"));
        assertThat(get.read("$.data.score", Integer.class)).isEqualTo(post.read("$.data.score", Integer.class));
        assertThat(get.read("$.data.riskLevel", String.class))
                .isEqualTo(post.read("$.data.riskLevel", String.class));
        // Deep-equals on the whole array proves both the content AND the order of every
        // finding round-trip unchanged; this URL deterministically fires at least two rules,
        // so the ordering proof is non-trivial rather than a one-element list trivially "in
        // order".
        assertThat(post.read("$.data.findings", List.class)).hasSizeGreaterThanOrEqualTo(2);
        assertThat(get.<Object>read("$.data.findings")).isEqualTo(post.<Object>read("$.data.findings"));
        assertThat(get.read("$.meta.engineVersion", String.class))
                .isEqualTo(post.read("$.meta.engineVersion", String.class));

        // The test clock (FixedClockConfig) is frozen at a microsecond-aligned instant, so
        // there is no sub-microsecond component for PostgreSQL to round on the way in; the
        // stored value is exactly the value written, and this comparison can be exact rather
        // than tolerant.
        Instant postAnalyzedAt = Instant.parse(post.read("$.data.analyzedAt", String.class));
        Instant getAnalyzedAt = Instant.parse(get.read("$.data.analyzedAt", String.class));
        assertThat(getAnalyzedAt).isEqualTo(postAnalyzedAt).isEqualTo(FIXED_INSTANT);

        assertThat(postBody).doesNotContain(querySecret, fragmentSecret);
        assertThat(getBody).doesNotContain(querySecret, fragmentSecret);
    }

    @Test
    @DisplayName("multiple retained scans stay isolated from each other")
    void multipleScansRemainIsolated() throws Exception {
        MvcResult firstPost = mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/first-account\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult secondPost = mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://other-example.org/second-page\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String firstScanId = JsonPath.parse(firstPost.getResponse().getContentAsString())
                .read("$.data.scanId", String.class);
        String secondScanId = JsonPath.parse(secondPost.getResponse().getContentAsString())
                .read("$.data.scanId", String.class);
        assertThat(firstScanId).isNotEqualTo(secondScanId);

        mockMvc.perform(get("/api/v1/scans/{scanId}", firstScanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.input").value("https://example.com/first-account"))
                .andExpect(jsonPath("$.data.normalized.host").value("example.com"));

        mockMvc.perform(get("/api/v1/scans/{scanId}", secondScanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.input").value("http://other-example.org/second-page"))
                .andExpect(jsonPath("$.data.normalized.host").value("other-example.org"));
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
