package com.lyanhkhoa.linksentry.common.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.lyanhkhoa.linksentry.license.api.CreateLicenseRequest;
import com.lyanhkhoa.linksentry.license.application.LicenseAdminService;
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
 * interfere with each other despite sharing one cached Spring context. {@code
 * linksentry.ratelimit.scan.capacity} is overridden to a small, exact value in the
 * licensed-device-bypass test so the general rate limiter's own independent 429 can
 * be triggered deterministically within a handful of requests, without waiting on
 * real refill timing.
 */
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

    @Autowired
    private LicenseAdminService licenseAdminService;

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
        jdbcTemplate.update("DELETE FROM device_license_assignment");
        jdbcTemplate.update("DELETE FROM license");
        jdbcTemplate.update("DELETE FROM device_installation");
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
                .andExpect(jsonPath("$.message").value("Request a license to continue scanning."));
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
            "a licensed device's scan from an exhausted IP succeeds and persists; the general rate limiter still independently applies")
    void licensedDeviceBypassesExhaustedTrialButRateLimitStillApplies() throws Exception {
        String address = "203.0.113.20";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(scanRequest(address, "https://example.com/account")).andExpect(status().isOk());
        }
        mockMvc.perform(scanRequest(address, "https://example.com/account"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ANONYMOUS_TRIAL_EXHAUSTED"));
        assertThat(scanCount()).isZero();

        MvcResult bootstrapResult = mockMvc.perform(post("/api/v1/devices").with(withRemoteAddr(address)))
                .andExpect(status().isOk())
                .andReturn();
        DocumentContext bootstrap = JsonPath.parse(bootstrapResult.getResponse().getContentAsString());
        String activationCode = bootstrap.read("$.activationCode", String.class);
        String credential = bootstrap.read("$.credential", String.class);
        UUID licenseId = licenseAdminService
                .create(new CreateLicenseRequest("trial-bypass-test-" + UUID.randomUUID(), null, null))
                .licenseId();
        licenseAdminService.grantDevice(licenseId, activationCode);
        String authorizationHeader = "Device " + credential;

        // The trial guard never gates this licensed device, even from the same
        // exhausted address: both licensed scans succeed and persist.
        mockMvc.perform(scanRequest(address, "https://example.com/account")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanId").isNotEmpty());
        assertThat(scanCount()).isEqualTo(1);

        mockMvc.perform(scanRequest(address, "https://example.com/account")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk());
        assertThat(scanCount()).isEqualTo(2);

        // scan.capacity=6 above: 3 accepted anonymous + 1 trial-rejected (still
        // consumes a rate-limit token, per RateLimitFilter's documented behaviour) +
        // 2 licensed successes = 6 tokens spent. The general rate limiter — not
        // the trial guard, which would let a licensed device through — is what
        // stops this 7th request; the code proves which control fired.
        mockMvc.perform(scanRequest(address, "https://example.com/account")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
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
