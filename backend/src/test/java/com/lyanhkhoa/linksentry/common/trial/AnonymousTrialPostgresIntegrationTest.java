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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@link AnonymousTrialFilter} against the real Spring Security chain, real controllers,
 * and the real PostgreSQL-persisted device-scoped quota (ADR 0010) — not a mocked slice. Identity
 * is now the presented device credential, not a remote address, so every test bootstraps its own
 * independent device rather than picking a distinct fake IP the way the retired heap-store version
 * of this test did. Migration/schema, exact-boundary, concurrency, cross-context, cascade, and
 * stale-sweep proof live in {@code common.trial.persistence.DeviceTrialQuotaPostgresIntegrationTest}.
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
        "linksentry.ratelimit.scan.capacity=100",
        "linksentry.ratelimit.scan.refill-per-minute=100",
        "linksentry.ratelimit.scan-lookup.capacity=100",
        "linksentry.anonymous-trial.max-scans=3",
        "linksentry.anonymous-trial.window=24h"
})
class AnonymousTrialPostgresIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-21T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("linksentry_trial_filter_test")
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
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    @DisplayName("the first 3 trial scans from one device succeed without persisting; the 4th is a safe 429")
    void firstThreeTrialScansSucceedFourthIsExhausted() throws Exception {
        String credential = bootstrapDevice();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(scanRequest(credential, "https://example.com/account"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.scanId").value(nullValue()));
        }
        assertThat(scanCount()).isZero();

        mockMvc.perform(scanRequest(credential, "https://example.com/account"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ANONYMOUS_TRIAL_EXHAUSTED"))
                .andExpect(jsonPath("$.message").value("Request a license to continue scanning."));
        assertThat(scanCount()).isZero();
    }

    @Test
    @DisplayName("two different devices from the same remote address each get an independent quota")
    void twoDevicesFromSameAddressAreIndependent() throws Exception {
        String firstCredential = bootstrapDevice();
        String secondCredential = bootstrapDevice();
        String sharedAddress = "203.0.113.50";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(scanRequest(firstCredential, "https://example.com/account").with(withRemoteAddr(sharedAddress)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(scanRequest(firstCredential, "https://example.com/account").with(withRemoteAddr(sharedAddress)))
                .andExpect(status().isTooManyRequests());

        // Same address, a different device: untouched quota.
        mockMvc.perform(scanRequest(secondCredential, "https://example.com/account").with(withRemoteAddr(sharedAddress)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("no Authorization header at all gets the fixed 401 TRIAL_DEVICE_REQUIRED")
    void noCredentialIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/account\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TRIAL_DEVICE_REQUIRED"))
                .andExpect(jsonPath("$.message").value("A valid device credential is required to use the trial."));
        assertThat(scanCount()).isZero();
    }

    @Test
    @DisplayName("a malformed Authorization scheme gets the identical 401 TRIAL_DEVICE_REQUIRED")
    void malformedCredentialIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer some-old-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/account\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TRIAL_DEVICE_REQUIRED"));
    }

    @Test
    @DisplayName("a credential matching no known device gets the identical 401 TRIAL_DEVICE_REQUIRED")
    void unknownCredentialIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Device totally-unknown-credential")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/account\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TRIAL_DEVICE_REQUIRED"));
    }

    @Test
    @DisplayName("a pending device (bootstrapped, never granted a license) gets exactly 3 trial scans per 24h")
    void pendingDeviceGetsExactlyThreeScans() throws Exception {
        String credential = bootstrapDevice();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(scanRequest(credential, "https://example.com/account")).andExpect(status().isOk());
        }
        mockMvc.perform(scanRequest(credential, "https://example.com/account"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ANONYMOUS_TRIAL_EXHAUSTED"));
    }

    @Test
    @DisplayName("a revoked device (previously licensed, now revoked) also gets exactly 3 trial scans per 24h")
    void revokedDeviceGetsExactlyThreeScans() throws Exception {
        DocumentContext bootstrap = bootstrapDeviceResponse();
        String activationCode = bootstrap.read("$.activationCode", String.class);
        String credential = bootstrap.read("$.credential", String.class);
        UUID licenseId = licenseAdminService
                .create(new CreateLicenseRequest("revoked-device-test-" + UUID.randomUUID(), null, null))
                .licenseId();
        licenseAdminService.grantDevice(licenseId, activationCode);
        var deviceId = licenseAdminService.findDeviceByActivationCode(activationCode).deviceId();
        licenseAdminService.revokeDevice(deviceId);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(scanRequest(credential, "https://example.com/account")).andExpect(status().isOk());
        }
        mockMvc.perform(scanRequest(credential, "https://example.com/account"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ANONYMOUS_TRIAL_EXHAUSTED"));
        assertThat(scanCount()).isZero();
    }

    @Test
    @DisplayName("a licensed device's scan succeeds and persists, without ever calling the trial quota")
    void licensedDeviceBypassesTrialQuotaEntirely() throws Exception {
        DocumentContext bootstrap = bootstrapDeviceResponse();
        String activationCode = bootstrap.read("$.activationCode", String.class);
        String credential = bootstrap.read("$.credential", String.class);
        UUID licenseId = licenseAdminService
                .create(new CreateLicenseRequest("trial-bypass-test-" + UUID.randomUUID(), null, null))
                .licenseId();
        licenseAdminService.grantDevice(licenseId, activationCode);
        String authorizationHeader = "Device " + credential;

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/scans")
                            .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"https://example.com/account\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.scanId").isNotEmpty());
        }
        assertThat(scanCount()).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM device_trial_scan_event", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("an exhausted trial device becomes licensed mid-window and immediately scans again unlimited")
    void deviceLicensedAfterExhaustionBypassesImmediately() throws Exception {
        DocumentContext bootstrap = bootstrapDeviceResponse();
        String activationCode = bootstrap.read("$.activationCode", String.class);
        String credential = bootstrap.read("$.credential", String.class);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(scanRequest(credential, "https://example.com/account")).andExpect(status().isOk());
        }
        mockMvc.perform(scanRequest(credential, "https://example.com/account")).andExpect(status().isTooManyRequests());

        UUID licenseId = licenseAdminService
                .create(new CreateLicenseRequest("post-exhaustion-license-" + UUID.randomUUID(), null, null))
                .licenseId();
        licenseAdminService.grantDevice(licenseId, activationCode);

        mockMvc.perform(post("/api/v1/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Device " + credential)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/account\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanId").isNotEmpty());
    }

    @Test
    @DisplayName("the 429 and 401 bodies never leak a device id, credential, or remote address")
    void rejectionBodiesNeverLeakIdentity() throws Exception {
        String credential = bootstrapDevice();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(scanRequest(credential, "https://example.com/account")).andExpect(status().isOk());
        }

        MvcResult exhausted = mockMvc.perform(scanRequest(credential, "https://example.com/account"))
                .andExpect(status().isTooManyRequests())
                .andReturn();
        String exhaustedBody = exhausted.getResponse().getContentAsString();
        assertThat(exhaustedBody)
                .doesNotContain(credential)
                .doesNotContainIgnoringCase("device")
                .doesNotContainIgnoringCase("remaining")
                .doesNotContainIgnoringCase("reset");
        assertThat(exhausted.getResponse().getHeaderNames())
                .noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("retry"));

        MvcResult unauthorized = mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/account\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(unauthorized.getResponse().getContentAsString()).doesNotContain(credential);
    }

    private String bootstrapDevice() throws Exception {
        return bootstrapDeviceResponse().read("$.credential", String.class);
    }

    private DocumentContext bootstrapDeviceResponse() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/devices")).andExpect(status().isOk()).andReturn();
        return JsonPath.parse(result.getResponse().getContentAsString());
    }

    private int scanCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scan_history", Integer.class);
    }

    private static MockHttpServletRequestBuilder scanRequest(String credential, String url) {
        return post("/api/v1/scans")
                .header(HttpHeaders.AUTHORIZATION, "Device " + credential)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\"}");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor withRemoteAddr(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
