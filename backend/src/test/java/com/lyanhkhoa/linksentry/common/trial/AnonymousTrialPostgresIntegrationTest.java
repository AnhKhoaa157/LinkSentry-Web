package com.lyanhkhoa.linksentry.common.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@link AnonymousTrialFilter} against the real Spring Security chain, real
 * controllers, and a real PostgreSQL-backed persistence layer — not a mocked slice.
 *
 * <p>Each test method uses its own dedicated fake remote address so methods never
 * interfere with each other despite sharing one cached Spring context (same pattern
 * as {@code AuthPostgresIntegrationTest}). {@code linksentry.ratelimit.scan.capacity}
 * is overridden to a small, exact value in the authenticated-bypass test so the
 * general rate limiter's own independent 429 can be triggered deterministically
 * within a handful of requests, without waiting on real refill timing.
 */
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
        "linksentry.ratelimit.scan.capacity=6",
        "linksentry.ratelimit.scan.refill-per-minute=6",
        "linksentry.ratelimit.scan-lookup.capacity=100",
        "linksentry.anonymous-trial.max-scans=3",
        "linksentry.anonymous-trial.window=24h"
})
class AnonymousTrialPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("linksentry_trial_test")
            .withUsername("linksentry")
            .withPassword("integration-test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("the first 3 anonymous scans from one IPv4 address succeed without persisting; the 4th is a safe 429")
    void firstThreeAnonymousScansSucceedFourthIsExhausted() throws Exception {
        String address = "203.0.113.10";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(scanRequest(address, "https://example.com/account"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.scanId").value(nullValue()));
        }
        assertThat(scanCount()).isZero();

        mockMvc.perform(scanRequest(address, "https://example.com/account"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ANONYMOUS_TRIAL_EXHAUSTED"))
                .andExpect(jsonPath("$.message").value("Sign in to continue scanning."));
        assertThat(scanCount()).isZero();
    }

    @Test
    @DisplayName("IPv6 gets its own independent 3-scan quota")
    void ipv6HasIndependentQuota() throws Exception {
        String address = "2001:db8:85a3::8a2e:370:7334";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(scanRequest(address, "https://example.com/account")).andExpect(status().isOk());
        }
        assertThat(scanCount()).isZero();

        mockMvc.perform(scanRequest(address, "https://example.com/account"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ANONYMOUS_TRIAL_EXHAUSTED"));
        assertThat(scanCount()).isZero();
    }

    @Test
    @DisplayName(
            "an authenticated scan from an exhausted IP succeeds and persists; the general rate limiter still independently applies")
    void authenticatedCallerBypassesExhaustedTrialButRateLimitStillApplies() throws Exception {
        String address = "203.0.113.20";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(scanRequest(address, "https://example.com/account")).andExpect(status().isOk());
        }
        mockMvc.perform(scanRequest(address, "https://example.com/account"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ANONYMOUS_TRIAL_EXHAUSTED"));
        assertThat(scanCount()).isZero();

        String email = "user-" + UUID.randomUUID() + "@example.com";
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .with(withRemoteAddr(address))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.parse(registerResult.getResponse().getContentAsString())
                .read("$.accessToken", String.class);

        // The trial guard never gates this authenticated caller, even from the same
        // exhausted address: both authenticated scans succeed and persist.
        mockMvc.perform(scanRequest(address, "https://example.com/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanId").isNotEmpty());
        assertThat(scanCount()).isEqualTo(1);

        mockMvc.perform(scanRequest(address, "https://example.com/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(scanCount()).isEqualTo(2);

        // scan.capacity=6 above: 3 accepted anonymous + 1 trial-rejected (still
        // consumes a rate-limit token, per RateLimitFilter's documented behaviour) +
        // 2 authenticated successes = 6 tokens spent. The general rate limiter — not
        // the trial guard, which would let an authenticated caller through — is what
        // stops this 7th request; the code proves which control fired.
        mockMvc.perform(scanRequest(address, "https://example.com/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        assertThat(scanCount()).isEqualTo(2);
    }

    private int scanCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scan_history", Integer.class);
    }

    private static MockHttpServletRequestBuilder scanRequest(String address, String url) {
        return post("/api/v1/scans")
                .with(withRemoteAddr(address))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\"}");
    }

    private static RequestPostProcessor withRemoteAddr(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
