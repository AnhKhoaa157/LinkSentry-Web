package com.lyanhkhoa.linksentry.history.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import com.lyanhkhoa.linksentry.history.application.ScanHistoryRetentionService;
import com.lyanhkhoa.linksentry.history.application.ScanHistoryService;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.StoredFinding;
import com.lyanhkhoa.linksentry.history.domain.StoredNormalizedUrl;
import com.lyanhkhoa.linksentry.license.api.CreateLicenseRequest;
import com.lyanhkhoa.linksentry.license.application.LicenseAdminService;
import com.lyanhkhoa.linksentry.scan.application.ScanService;
import java.sql.Timestamp;
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
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
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
@TestPropertySource(properties = {
        "linksentry.ratelimit.device.capacity=100",
        "linksentry.ratelimit.device.refill-per-minute=100",
        "linksentry.ratelimit.scan.capacity=100",
        "linksentry.ratelimit.scan-lookup.capacity=100"
})
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
    private LicenseAdminService licenseAdminService;

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
        jdbcTemplate.update("DELETE FROM device_license_assignment");
        jdbcTemplate.update("DELETE FROM license");
        jdbcTemplate.update("DELETE FROM device_installation");
        jdbcTemplate.update("DELETE FROM auth_session");
        jdbcTemplate.update("DELETE FROM user_account");
    }

    @Test
    @DisplayName("Flyway applies the history and licensing migrations to an empty PostgreSQL database")
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
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.license')", String.class))
                .isEqualTo("license");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.device_installation')", String.class))
                .isEqualTo("device_installation");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.device_license_assignment')", String.class))
                .isEqualTo("device_license_assignment");
        // V2/V3 auth tables are retired in place, not dropped — see V4's migration comment.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.user_account')", String.class))
                .isEqualTo("user_account");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.auth_session')", String.class))
                .isEqualTo("auth_session");
    }

    @Test
    @DisplayName("save and retrieval preserve every public field and finding order")
    void roundTripsSafeSnapshotAndFindingOrder() throws Exception {
        TestDevice device = licensedDevice();
        UUID scanId = UUID.fromString("2ce16fb9-d52d-4310-8d45-a4e48f31889e");
        ScanHistory original = snapshot(scanId, Instant.parse("2026-08-16T12:00:00Z"), device.licenseId());

        historyService.save(original);

        assertThat(historyService.findRetained(scanId, device.licenseId())).contains(original);
    }

    @Test
    @DisplayName("raw query, fragment, and credential values never enter stored columns")
    void sensitiveUrlPartsNeverPersist() throws Exception {
        TestDevice device = licensedDevice();
        String querySecret = "query-secret-123";
        String fragmentSecret = "fragment-secret-456";
        String rawUrl = "https://example.com/account?token=" + querySecret + "#" + fragmentSecret;
        int rowsBeforeScan = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scan_history", Integer.class);

        var response = scanService.scan(rawUrl, device.licenseId());

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
    void expiredRecordsAreUnavailableAndPurged() throws Exception {
        TestDevice device = licensedDevice();
        UUID scanId = UUID.fromString("9f3d7a0c-421f-4d38-bc5d-5a57f2d4f3c1");
        historyService.save(snapshot(scanId, clock.instant().minusSeconds(31L * 24 * 60 * 60), device.licenseId()));

        assertThat(historyService.findRetained(scanId, device.licenseId())).isEmpty();
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
        TestDevice device = licensedDevice();
        UUID expiredScanId = UUID.fromString("5b1e9c3a-6f2d-4a7b-9e3c-2d1f8a6b4c0e");
        historyService.save(
                snapshot(expiredScanId, clock.instant().minusSeconds(31L * 24 * 60 * 60), device.licenseId()));

        mockMvc.perform(get("/api/v1/scans/{scanId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, device.authorizationHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested scan could not be found."));

        mockMvc.perform(get("/api/v1/scans/not-a-uuid").header(HttpHeaders.AUTHORIZATION, device.authorizationHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested scan could not be found."));

        // A record that genuinely existed but expired must be indistinguishable from one that
        // never existed at all: same status, same code, same message.
        mockMvc.perform(get("/api/v1/scans/{scanId}", expiredScanId)
                        .header(HttpHeaders.AUTHORIZATION, device.authorizationHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested scan could not be found."));
    }

    @Test
    @DisplayName("POST then GET round-trip every public response field through the real schema")
    void postThenGetRoundTripsEveryResponseField() throws Exception {
        TestDevice device = licensedDevice();
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
                        .header(HttpHeaders.AUTHORIZATION, device.authorizationHeader())
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();
        String postBody = postResult.getResponse().getContentAsString();
        DocumentContext post = JsonPath.parse(postBody);
        String scanId = post.read("$.data.scanId", String.class);

        MvcResult getResult = mockMvc.perform(get("/api/v1/scans/{scanId}", scanId)
                        .header(HttpHeaders.AUTHORIZATION, device.authorizationHeader()))
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
        TestDevice device = licensedDevice();
        MvcResult firstPost = mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, device.authorizationHeader())
                        .content("{\"url\":\"https://example.com/first-account\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult secondPost = mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, device.authorizationHeader())
                        .content("{\"url\":\"http://other-example.org/second-page\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String firstScanId = JsonPath.parse(firstPost.getResponse().getContentAsString())
                .read("$.data.scanId", String.class);
        String secondScanId = JsonPath.parse(secondPost.getResponse().getContentAsString())
                .read("$.data.scanId", String.class);
        assertThat(firstScanId).isNotEqualTo(secondScanId);

        mockMvc.perform(get("/api/v1/scans/{scanId}", firstScanId)
                        .header(HttpHeaders.AUTHORIZATION, device.authorizationHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.input").value("https://example.com/first-account"))
                .andExpect(jsonPath("$.data.normalized.host").value("example.com"));

        mockMvc.perform(get("/api/v1/scans/{scanId}", secondScanId)
                        .header(HttpHeaders.AUTHORIZATION, device.authorizationHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.input").value("http://other-example.org/second-page"))
                .andExpect(jsonPath("$.data.normalized.host").value("other-example.org"));
    }

    @Test
    @DisplayName("licensed history list is owner scoped, retention aware, and stable across equal timestamps")
    void licensedHistoryListIsPrivateRetainedAndStable() throws Exception {
        TestDevice ownerDevice = licensedDevice();
        TestDevice siblingDevice = licensedDevice(ownerDevice.licenseId());
        TestDevice otherLicenseDevice = licensedDevice();
        Instant tiedInstant = clock.instant().minusSeconds(60);
        UUID firstId = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");
        UUID secondId = UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
        UUID thirdId = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
        UUID olderId = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
        UUID expiredId = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        UUID crossLicenseId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID ownerlessId = UUID.fromString("99999999-9999-4999-8999-999999999999");

        historyService.save(snapshot(firstId, tiedInstant, ownerDevice.licenseId()));
        historyService.save(snapshot(secondId, tiedInstant, ownerDevice.licenseId()));
        historyService.save(snapshot(thirdId, tiedInstant, ownerDevice.licenseId()));
        historyService.save(snapshot(olderId, tiedInstant.minusSeconds(60), ownerDevice.licenseId()));
        historyService.save(snapshot(
                expiredId,
                clock.instant().minusSeconds(31L * 24 * 60 * 60),
                ownerDevice.licenseId()));
        historyService.save(snapshot(crossLicenseId, tiedInstant, otherLicenseDevice.licenseId()));
        historyService.save(snapshot(ownerlessId, tiedInstant, ownerDevice.licenseId()));
        jdbcTemplate.update("UPDATE scan_history SET owner_license_id = NULL WHERE scan_id = ?", ownerlessId);

        MvcResult firstPageResult = mockMvc.perform(get("/api/v1/scans")
                        .param("limit", "2")
                        .header(HttpHeaders.AUTHORIZATION, siblingDevice.authorizationHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].scanId").value(firstId.toString()))
                .andExpect(jsonPath("$.data.items[1].scanId").value(secondId.toString()))
                .andExpect(jsonPath("$.data.items[0].findingCount").value(2))
                .andExpect(jsonPath("$.meta.retentionDays").value(30))
                .andReturn();
        String firstPageBody = firstPageResult.getResponse().getContentAsString();
        String cursor = JsonPath.parse(firstPageBody).read("$.data.nextCursor", String.class);

        MvcResult secondPageResult = mockMvc.perform(get("/api/v1/scans")
                        .param("limit", "2")
                        .param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, siblingDevice.authorizationHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].scanId").value(thirdId.toString()))
                .andExpect(jsonPath("$.data.items[1].scanId").value(olderId.toString()))
                .andExpect(jsonPath("$.data.nextCursor").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();
        String allVisibleBodies = firstPageBody + secondPageResult.getResponse().getContentAsString();
        assertThat(allVisibleBodies)
                .doesNotContain(expiredId.toString(), crossLicenseId.toString(), ownerlessId.toString());
    }

    @Test
    @DisplayName("history list requires a licensed device and validates bounded pagination inputs")
    void historyListRequiresLicenseAndValidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/scans"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        TestDevice device = licensedDevice();
        mockMvc.perform(get("/api/v1/scans")
                        .param("limit", "0")
                        .header(HttpHeaders.AUTHORIZATION, device.authorizationHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.limit").exists());

        mockMvc.perform(get("/api/v1/scans")
                        .param("cursor", "not-a-cursor")
                        .header(HttpHeaders.AUTHORIZATION, device.authorizationHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.cursor").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("not-a-cursor"))));
    }

    @Test
    @DisplayName("filters and insights share license, retention, legacy, and deterministic aggregate boundaries")
    void filtersAndInsightsArePrivateRetainedAndDeterministic() throws Exception {
        TestDevice ownerDevice = licensedDevice();
        TestDevice otherLicenseDevice = licensedDevice();
        Instant now = clock.instant();
        UUID newestLow = UUID.fromString("f1111111-1111-4111-8111-111111111111");
        UUID newestHigh = UUID.fromString("f2222222-2222-4222-8222-222222222222");
        UUID previousHigh = UUID.fromString("f3333333-3333-4333-8333-333333333333");
        UUID olderModerate = UUID.fromString("f4444444-4444-4444-8444-444444444444");
        UUID expiredHigh = UUID.fromString("f5555555-5555-4555-8555-555555555555");
        UUID ownerlessId = UUID.fromString("f6666666-6666-4666-8666-666666666666");
        UUID legacyId = UUID.fromString("f7777777-7777-4777-8777-777777777777");
        UUID crossLicenseId = UUID.fromString("f8888888-8888-4888-8888-888888888888");
        UUID legacyUserId = UUID.fromString("f9999999-9999-4999-8999-999999999999");

        historyService.save(snapshotWith(
                newestLow, now.minusSeconds(60), ownerDevice.licenseId(), RiskLevel.LOW,
                List.of("MISSING_HTTPS"), "query-secret-insight"));
        historyService.save(snapshotWith(
                newestHigh, now.minusSeconds(120), ownerDevice.licenseId(), RiskLevel.HIGH,
                List.of("MISSING_HTTPS"), "fragment-secret-insight"));
        historyService.save(snapshotWith(
                previousHigh, now.minusSeconds(8 * 24 * 60 * 60L), ownerDevice.licenseId(), RiskLevel.HIGH,
                List.of("SUSPICIOUS_KEYWORDS"), "credential-secret-insight"));
        historyService.save(snapshotWith(
                olderModerate, now.minusSeconds(15 * 24 * 60 * 60L), ownerDevice.licenseId(), RiskLevel.MODERATE,
                List.of("SUSPICIOUS_KEYWORDS"), "evidence-secret-insight"));
        historyService.save(snapshotWith(
                expiredHigh, now.minusSeconds(31 * 24 * 60 * 60L), ownerDevice.licenseId(), RiskLevel.HIGH,
                List.of("MISSING_HTTPS"), "expired-secret"));
        historyService.save(snapshotWith(
                ownerlessId, now.minusSeconds(90), ownerDevice.licenseId(), RiskLevel.CRITICAL,
                List.of("MISSING_HTTPS"), "ownerless-secret"));
        historyService.save(snapshotWith(
                legacyId, now.minusSeconds(90), ownerDevice.licenseId(), RiskLevel.CRITICAL,
                List.of("MISSING_HTTPS"), "legacy-secret"));
        historyService.save(snapshotWith(
                crossLicenseId, now.minusSeconds(30), otherLicenseDevice.licenseId(), RiskLevel.CRITICAL,
                List.of("MISSING_HTTPS"), "cross-license-secret"));
        jdbcTemplate.update(
                "INSERT INTO user_account (user_id, email, password_hash, created_at) VALUES (?, ?, ?, ?)",
                legacyUserId,
                legacyUserId + "@example.test",
                "legacy-hash",
                Timestamp.from(now));
        jdbcTemplate.update("UPDATE scan_history SET owner_license_id = NULL WHERE scan_id = ?", ownerlessId);
        jdbcTemplate.update(
                "UPDATE scan_history SET owner_license_id = NULL, owner_user_id = ? WHERE scan_id = ?",
                legacyUserId,
                legacyId);

        mockMvc.perform(get("/api/v1/scans")
                        .param("riskLevel", "HIGH")
                        .param("ruleId", "MISSING_HTTPS")
                        .header(HttpHeaders.AUTHORIZATION, ownerDevice.authorizationHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].scanId").value(newestHigh.toString()));

        String listBody = mockMvc.perform(get("/api/v1/scans")
                        .header(HttpHeaders.AUTHORIZATION, ownerDevice.authorizationHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(listBody).doesNotContain(
                expiredHigh.toString(),
                ownerlessId.toString(),
                legacyId.toString(),
                crossLicenseId.toString());

        String insightsBody = mockMvc.perform(get("/api/v1/scans/insights")
                        .param("windowDays", "30")
                        .header(HttpHeaders.AUTHORIZATION, ownerDevice.authorizationHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windowDays").value(30))
                .andExpect(jsonPath("$.data.totalScans").value(4))
                .andExpect(jsonPath("$.data.scansLast7Days").value(2))
                .andExpect(jsonPath("$.data.previous7Days").value(1))
                .andExpect(jsonPath("$.data.riskDistribution.LOW").value(1))
                .andExpect(jsonPath("$.data.riskDistribution.MODERATE").value(1))
                .andExpect(jsonPath("$.data.riskDistribution.HIGH").value(2))
                .andExpect(jsonPath("$.data.riskDistribution.CRITICAL").value(0))
                .andExpect(jsonPath("$.data.topFindings[0].ruleId").value("MISSING_HTTPS"))
                .andExpect(jsonPath("$.data.topFindings[0].count").value(2))
                .andExpect(jsonPath("$.data.topFindings[1].ruleId").value("SUSPICIOUS_KEYWORDS"))
                .andExpect(jsonPath("$.data.topFindings[1].count").value(2))
                .andExpect(jsonPath("$.meta.retentionDays").value(30))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(insightsBody)
                .doesNotContain(
                        "query-secret-insight",
                        "fragment-secret-insight",
                        "credential-secret-insight",
                        "evidence-secret-insight",
                        "ownerless-secret",
                        "legacy-secret",
                        "cross-license-secret",
                        "ownerLicenseId",
                        "deviceId");

        mockMvc.perform(get("/api/v1/scans/insights")
                        .param("windowDays", "14")
                        .header(HttpHeaders.AUTHORIZATION, ownerDevice.authorizationHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("The request contains invalid values."))
                .andExpect(jsonPath("$.fieldErrors.windowDays").value("Enter 7 or 30 days."));
    }

    private ScanHistory snapshot(UUID scanId, Instant analyzedAt, UUID ownerLicenseId) {
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
                analyzedAt,
                ownerLicenseId);
    }

    private ScanHistory snapshotWith(
            UUID scanId,
            Instant analyzedAt,
            UUID ownerLicenseId,
            RiskLevel riskLevel,
            List<String> ruleIds,
            String evidence) {
        StoredNormalizedUrl normalized = new StoredNormalizedUrl(
                "https", "login.example.com", "login.example.com", "example.com", 443,
                "/Account/Reset", true, true);
        List<StoredFinding> findings = ruleIds.stream()
                .map(ruleId -> new StoredFinding(
                        ruleId,
                        Severity.MEDIUM,
                        15,
                        "Backend-owned title",
                        "Backend-owned explanation.",
                        evidence))
                .toList();
        return new ScanHistory(
                scanId,
                "https://login.example.com:443/Account/Reset",
                normalized,
                40,
                riskLevel,
                findings,
                "0.1.0",
                analyzedAt,
                ownerLicenseId);
    }

    /** Bootstraps a device over HTTP, then licenses it directly through the admin service. */
    private TestDevice licensedDevice() throws Exception {
        UUID licenseId = licenseAdminService
                .create(new CreateLicenseRequest("integration-test-" + UUID.randomUUID(), null, null))
                .licenseId();
        return licensedDevice(licenseId);
    }

    private TestDevice licensedDevice(UUID licenseId) throws Exception {
        MvcResult bootstrapResult = mockMvc.perform(post("/api/v1/devices"))
                .andExpect(status().isOk())
                .andReturn();
        DocumentContext bootstrap = JsonPath.parse(bootstrapResult.getResponse().getContentAsString());
        String activationCode = bootstrap.read("$.activationCode", String.class);
        String credential = bootstrap.read("$.credential", String.class);

        licenseAdminService.grantDevice(licenseId, activationCode);

        return new TestDevice(licenseId, credential);
    }

    private record TestDevice(UUID licenseId, String credential) {
        String authorizationHeader() {
            return "Device " + credential;
        }
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
