package com.lyanhkhoa.linksentry.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lyanhkhoa.linksentry.auth.security.TokenService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

/** PostgreSQL/Flyway proof of token lifecycle and owner isolation. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "linksentry.ratelimit.auth.capacity=100",
        "linksentry.ratelimit.auth.refill-per-minute=100",
        "linksentry.ratelimit.scan.capacity=100",
        "linksentry.ratelimit.scan-lookup.capacity=100"
})
class AuthPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("linksentry_auth_test")
            .withUsername("linksentry")
            .withPassword("integration-test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenService tokenService;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @AfterEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM scan_history");
        jdbcTemplate.update("DELETE FROM auth_session");
        jdbcTemplate.update("DELETE FROM user_account");
    }

    @Test
    @DisplayName("Flyway creates account/session tables and the bearer is never stored raw")
    void migrationAndTokenStorageAreSafe() throws Exception {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2'", Integer.class))
                .isEqualTo(1);

        TestUser user = registerUser();
        String storedPassword = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user_account WHERE user_id = ?", String.class, user.userId());
        String storedTokenHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM auth_session WHERE user_id = ?", String.class, user.userId());

        assertThat(storedPassword).startsWith("$2").doesNotContain(user.password());
        assertThat(storedTokenHash).hasSize(64).isNotEqualTo(user.token());
        assertThat(storedTokenHash).isEqualTo(tokenService.sha256(user.token()));

        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(user.email()))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("logout revokes a session and an expired session is rejected with the same safe 401")
    void revokedAndExpiredTokensAreRejected() throws Exception {
        TestUser revoked = registerUser();
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, revoked.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, revoked.bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        TestUser expired = registerUser();
        Instant expiredAt = Instant.now().minusSeconds(60);
        jdbcTemplate.update(
                "UPDATE auth_session SET created_at = ?, expires_at = ? WHERE token_hash = ?",
                expiredAt.minusSeconds(60),
                expiredAt,
                tokenService.sha256(expired.token()));
        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, expired.bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource."));
    }

    @Test
    @DisplayName("anonymous scans are not persisted, while an authenticated scan is saved once for its owner")
    void anonymousAndAuthenticatedPersistenceDiffer() throws Exception {
        String querySecret = "postgres-query-sentinel";
        String fragmentSecret = "postgres-fragment-sentinel";
        String url = "https://example.com/account?token=" + querySecret + "#" + fragmentSecret;
        int before = scanCount();

        MvcResult anonymous = mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanId").value(nullValue()))
                .andReturn();
        assertThat(anonymous.getResponse().getContentAsString()).doesNotContain(querySecret, fragmentSecret);
        assertThat(scanCount()).isEqualTo(before);

        TestUser user = registerUser();
        MvcResult authenticated = mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .content("{\"url\":\"" + url + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanId").isNotEmpty())
                .andExpect(jsonPath("$.data.input").value("https://example.com/account"))
                .andReturn();
        String scanId = JsonPath.parse(authenticated.getResponse().getContentAsString())
                .read("$.data.scanId", String.class);
        String stored = jdbcTemplate.queryForObject(
                "SELECT redacted_display_value || '|' || host || '|' || path FROM scan_history WHERE scan_id = ?",
                String.class, UUID.fromString(scanId));

        assertThat(scanCount()).isEqualTo(before + 1);
        assertThat(stored).doesNotContain(querySecret, fragmentSecret);
        mockMvc.perform(get("/api/v1/scans/{scanId}", scanId)
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/scans/{scanId}", scanId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("cross-user, ownerless, malformed, and missing history all collapse to safe 404")
    void historyIsPrivateAndOwnerlessRowsStayDark() throws Exception {
        TestUser owner = registerUser();
        TestUser other = registerUser();
        MvcResult created = mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .content("{\"url\":\"https://owner.example/account\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String ownedScanId = JsonPath.parse(created.getResponse().getContentAsString())
                .read("$.data.scanId", String.class);

        mockMvc.perform(get("/api/v1/scans/{scanId}", ownedScanId)
                        .header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested scan could not be found."));

        UUID ownerlessScanId = UUID.randomUUID();
        insertOwnerless(ownerlessScanId);
        mockMvc.perform(get("/api/v1/scans/{scanId}", ownerlessScanId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/scans/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"));
    }

    private TestUser registerUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-123";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.parse(result.getResponse().getContentAsString()).read("$.accessToken", String.class);
        UUID userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM user_account WHERE email = ?", UUID.class, email);
        return new TestUser(email, password, userId, token);
    }

    private int scanCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scan_history", Integer.class);
    }

    private void insertOwnerless(UUID scanId) {
        jdbcTemplate.update(
                """
                INSERT INTO scan_history (
                    scan_id, redacted_display_value, scheme, host, ascii_host, registrable_domain,
                    port, path, query_present, fragment_present, score, risk_level, engine_version, analyzed_at,
                    owner_user_id
                ) VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, NULL)
                """,
                scanId,
                "https://legacy.example/",
                "https",
                "legacy.example",
                "legacy.example",
                "legacy.example",
                "/",
                false,
                false,
                0,
                "LOW",
                "0.1.0",
                Instant.parse("2026-08-18T12:00:00Z"));
    }

    private record TestUser(String email, String password, UUID userId, String token) {
        String bearer() {
            return "Bearer " + token;
        }
    }
}
