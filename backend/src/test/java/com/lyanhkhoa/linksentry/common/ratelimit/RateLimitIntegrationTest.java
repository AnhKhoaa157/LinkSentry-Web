package com.lyanhkhoa.linksentry.common.ratelimit;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Exercises {@link RateLimitFilter} through the real Spring Security chain and
 * controllers, rather than through mocks.
 *
 * <p>Capacities are overridden to 2 (refill effectively frozen at 1/minute) so each
 * method can exhaust a bucket in a handful of requests without waiting on real
 * refill timing. {@code @DirtiesContext} forces a fresh {@link RateLimitBucketStore}
 * per test method: MockMvc always presents {@code 127.0.0.1} as the remote address,
 * so bucket state would otherwise leak between methods sharing one cached context.
 *
 * <p>Every request below is deliberately invalid — an unsupported scheme for POST, a
 * non-UUID path segment for GET — so it is rejected before ever reaching persistence.
 * The {@code test} profile's H2 instance has no schema (Flyway is disabled; see
 * {@code application-test.yml} and {@link com.lyanhkhoa.linksentry.common.security.CorsConfigurationTest}'s
 * class Javadoc for the same constraint). This class proves the limiter, not the
 * scan feature, so it must not depend on a successful save.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "linksentry.ratelimit.scan.capacity=2",
            "linksentry.ratelimit.scan.refill-per-minute=1",
            "linksentry.ratelimit.scan-lookup.capacity=2",
            "linksentry.ratelimit.scan-lookup.refill-per-minute=1"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RateLimitIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String DISALLOWED_ORIGIN = "https://evil.example";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST and GET buckets are independent: exhausting one leaves the other untouched")
    void postAndGetLimitsAreIndependent() throws Exception {
        mockMvc.perform(invalidPost()).andExpect(status().isBadRequest());
        mockMvc.perform(invalidPost()).andExpect(status().isBadRequest());
        mockMvc.perform(invalidPost())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        mockMvc.perform(get("/api/v1/scans/not-a-uuid-1")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/scans/not-a-uuid-2")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/scans/not-a-uuid-3"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("malformed JSON and an invalid URL share the POST bucket; a malformed id consumes the GET bucket")
    void invalidRequestsStillConsumeQuota() throws Exception {
        mockMvc.perform(post("/api/v1/scans").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(invalidPost())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_URL"));
        mockMvc.perform(invalidPost()).andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/v1/scans/not-a-uuid-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/scans/not-a-uuid-2")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/scans/not-a-uuid-3")).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("OPTIONS preflight and the health endpoint never consume quota")
    void optionsAndHealthDoNotConsumeQuota() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(options("/api/v1/scans")
                            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        }

        mockMvc.perform(invalidPost()).andExpect(status().isBadRequest());
        mockMvc.perform(invalidPost()).andExpect(status().isBadRequest());
        mockMvc.perform(invalidPost()).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("an allowed-origin 429 keeps its CORS header; a disallowed origin stays rejected regardless")
    void corsIsPreservedAroundRateLimiting() throws Exception {
        mockMvc.perform(invalidPost().header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
        mockMvc.perform(invalidPost().header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));

        mockMvc.perform(invalidPost().header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));

        mockMvc.perform(invalidPost().header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("a 429 never contains the submitted URL, its query or fragment, or any RateLimit-* header")
    void rejectionNeverLeaksSensitiveRequestDetail() throws Exception {
        String secretQuery = "top-secret-token";
        String secretFragment = "top-secret-fragment";
        String sensitiveUrl = "ftp://example.com/account?token=" + secretQuery + "#" + secretFragment;

        mockMvc.perform(postWithUrl(sensitiveUrl)).andExpect(status().isBadRequest());
        mockMvc.perform(postWithUrl(sensitiveUrl)).andExpect(status().isBadRequest());

        MvcResult result = mockMvc.perform(postWithUrl(sensitiveUrl))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("[0-9]+")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Assertions.assertThat(body)
                .doesNotContain(secretQuery, secretFragment, "example.com/account", "127.0.0.1", "ftp://");
        for (String headerName : result.getResponse().getHeaderNames()) {
            Assertions.assertThat(headerName.toLowerCase(Locale.ROOT)).doesNotContain("ratelimit");
        }
    }

    private static MockHttpServletRequestBuilder invalidPost() {
        return postWithUrl("ftp://example.com/file");
    }

    private static MockHttpServletRequestBuilder postWithUrl(String url) {
        return post("/api/v1/scans").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"" + url + "\"}");
    }
}
