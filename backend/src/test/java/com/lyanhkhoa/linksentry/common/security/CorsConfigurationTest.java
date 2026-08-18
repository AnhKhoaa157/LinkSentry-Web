package com.lyanhkhoa.linksentry.common.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves CORS accepts only the origin configured in {@code linksentry.cors.allowed-origins}
 * (bound to {@code http://localhost:5173} by {@code application-test.yml}), exercised through
 * the real Spring Security filter chain rather than a mocked slice.
 *
 * <p>Spring's {@code CorsFilter} rejects both preflight and actual cross-origin requests with a
 * {@code 403} and no {@code Access-Control-Allow-Origin} header the moment the {@code Origin}
 * does not match — before the request ever reaches a controller.
 *
 * <p>The actual-request (non-preflight) cases target {@code /api/v1/health} rather than the scan
 * endpoints: it is documented to never touch the database, and this class's {@code test} profile
 * runs against an empty H2 instance (Flyway disabled, {@code ddl-auto: none} — see
 * {@code application-test.yml}), so a database-backed route would fail for a reason unrelated to
 * CORS. Preflight requests are unaffected either way, since Spring's {@code CorsFilter}
 * short-circuits them before they reach any handler.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigurationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String DISALLOWED_ORIGIN = "https://evil.example";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a GET from the configured origin is processed normally and echoes that origin back")
    void allowedOriginReceivesCorsHeaderAndIsProcessed() throws Exception {
        mockMvc.perform(get("/api/v1/health").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isOk()) // request reached the controller, not just the filter.
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    @Test
    @DisplayName("a GET from an unconfigured origin is rejected before reaching the controller")
    void disallowedOriginIsRejectedWithNoCorsHeader() throws Exception {
        mockMvc.perform(get("/api/v1/health").header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("a preflight from the configured origin allows POST")
    void preflightFromAllowedOriginAllowsPost() throws Exception {
        mockMvc.perform(options("/api/v1/scans")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")));
    }

    @Test
    @DisplayName("an authenticated preflight allows the Authorization header")
    void preflightAllowsAuthorizationHeader() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Authorization")));
    }

    @Test
    @DisplayName("a preflight from an unconfigured origin is rejected with no CORS header")
    void preflightFromDisallowedOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/scans")
                        .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
