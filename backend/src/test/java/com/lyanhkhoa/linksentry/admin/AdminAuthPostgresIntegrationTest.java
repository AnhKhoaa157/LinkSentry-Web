package com.lyanhkhoa.linksentry.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lyanhkhoa.linksentry.admin.application.AdminAuthService;
import com.lyanhkhoa.linksentry.admin.security.AdminTokenService;
import java.sql.Timestamp;
import java.time.Instant;
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

/**
 * PostgreSQL/Flyway proof of admin bootstrap, hashed-session lifecycle, and protected-route
 * rejection. {@code AdminBootstrapRunner} runs once as this class's shared Spring context starts,
 * using the fixed bootstrap credentials below — every test method logs in with them rather than
 * re-registering, since there is no admin self-service registration endpoint.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "linksentry.admin-auth.bootstrap-username=integration-test-admin",
        "linksentry.admin-auth.bootstrap-password=correct-horse-battery-staple",
        "linksentry.admin-auth.session-ttl=30m",
        // Generous overrides: many test methods share one context and one MockMvc remote
        // address, so the default strict admin-auth-login bucket would otherwise start
        // rejecting legitimate logins partway through the class.
        "linksentry.ratelimit.scan.capacity=1000",
        "linksentry.ratelimit.scan-lookup.capacity=1000",
        "linksentry.ratelimit.admin-auth-login.capacity=1000"
})
class AdminAuthPostgresIntegrationTest {

    private static final String BOOTSTRAP_USERNAME = "integration-test-admin";
    private static final String BOOTSTRAP_PASSWORD = "correct-horse-battery-staple";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("linksentry_admin_auth_test")
            .withUsername("linksentry")
            .withPassword("integration-test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminTokenService tokenService;

    @Autowired
    private AdminAuthService adminAuthService;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    @DisplayName("Flyway creates admin_user/admin_session and bootstrap creates exactly one admin, only once")
    void migrationAndBootstrapAreSafe() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_user", Integer.class)).isEqualTo(1);

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM admin_user WHERE username = ?", String.class, BOOTSTRAP_USERNAME);
        assertThat(storedHash).startsWith("$2").doesNotContain(BOOTSTRAP_PASSWORD);

        // Re-running bootstrap (as a second instance's startup would) must not create a second admin.
        adminAuthService.bootstrapIfNeeded();

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_user", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("login stores only a hashed session token, and the safe session endpoint reflects it")
    void loginStoresHashedSessionToken() throws Exception {
        String token = loginAdmin();

        String storedTokenHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM admin_session WHERE token_hash = ?", String.class, tokenService.sha256(token));
        assertThat(storedTokenHash).hasSize(64).isNotEqualTo(token).isEqualTo(tokenService.sha256(token));

        mockMvc.perform(get("/api/v1/admin-auth/session").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin.username").value(BOOTSTRAP_USERNAME))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    @DisplayName("invalid login is rejected without creating a session")
    void invalidLoginIsRejected() throws Exception {
        int before = sessionCount();

        mockMvc.perform(post("/api/v1/admin-auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + BOOTSTRAP_USERNAME + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        assertThat(sessionCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("logout revokes a session and an expired session is rejected with the same safe 401")
    void revokedAndExpiredTokensAreRejected() throws Exception {
        String revokedToken = loginAdmin();
        mockMvc.perform(post("/api/v1/admin-auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + revokedToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin-auth/session").header(HttpHeaders.AUTHORIZATION, "Bearer " + revokedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String expiredToken = loginAdmin();
        Instant expiredAt = Instant.now().minusSeconds(60);
        jdbcTemplate.update(
                "UPDATE admin_session SET created_at = ?, expires_at = ? WHERE token_hash = ?",
                Timestamp.from(expiredAt.minusSeconds(60)),
                Timestamp.from(expiredAt),
                tokenService.sha256(expiredToken));
        mockMvc.perform(get("/api/v1/admin-auth/session").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource."));
    }

    @Test
    @DisplayName("protected admin-auth routes reject a missing or unrecognised bearer with the same safe 401")
    void protectedRoutesRejectMissingOrInvalidBearer() throws Exception {
        mockMvc.perform(get("/api/v1/admin-auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/v1/admin-auth/logout")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin-auth/session")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    private String loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin-auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + BOOTSTRAP_USERNAME + "\",\"password\":\"" + BOOTSTRAP_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.accessToken", String.class);
    }

    private int sessionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_session", Integer.class);
    }
}
