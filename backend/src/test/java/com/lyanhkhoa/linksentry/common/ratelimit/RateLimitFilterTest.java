package com.lyanhkhoa.linksentry.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Exercises {@link RateLimitFilter} end to end against real {@link RateLimitBucketStore}
 * and {@link RouteClassifier} instances, using Spring's servlet mocks rather than
 * Mockito doubles for the request/response — no Bucket4j type needs mocking, and the
 * response assertions read exactly what a real client would receive.
 */
class RateLimitFilterTest {

    private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");

    @Test
    @DisplayName("a request within capacity passes through untouched")
    void withinCapacityPassesThrough() throws Exception {
        RateLimitFilter filter = newFilter(properties(1, true));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = postRequest("203.0.113.5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("a request past capacity is rejected with 429, Retry-After, and the RATE_LIMITED envelope")
    void overCapacityIsRejected() throws Exception {
        RateLimitFilter filter = newFilter(properties(1, true));
        filter.doFilter(postRequest("203.0.113.5"), new MockHttpServletResponse(), mock(FilterChain.class));

        FilterChain secondChain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(postRequest("203.0.113.5"), response, secondChain);

        verifyNoInteractions(secondChain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).startsWith("application/json");
        String retryAfter = response.getHeader("Retry-After");
        assertThat(retryAfter).isNotBlank();
        assertThat(Long.parseLong(retryAfter)).isPositive();
        assertThat(response.getContentAsString())
                .contains("\"code\":\"RATE_LIMITED\"")
                .contains("Too many requests. Please slow down and try again shortly.")
                .contains("\"traceId\"");
    }

    @Test
    @DisplayName("disabled mode never rejects, even once an identity would otherwise be exhausted")
    void disabledModeAlwaysPassesThrough() throws Exception {
        RateLimitFilter filter = newFilter(properties(1, false));

        filter.doFilter(postRequest("203.0.113.5"), new MockHttpServletResponse(), mock(FilterChain.class));

        FilterChain secondChain = mock(FilterChain.class);
        MockHttpServletRequest secondRequest = postRequest("203.0.113.5");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, secondChain);

        verify(secondChain).doFilter(secondRequest, secondResponse);
        assertThat(secondResponse.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("identity comes only from getRemoteAddr; spoofed forwarding headers change nothing")
    void identityIgnoresForwardingHeaders() throws Exception {
        RateLimitFilter filter = newFilter(properties(1, true));

        MockHttpServletRequest first = postRequest("203.0.113.5");
        first.addHeader("X-Forwarded-For", "1.1.1.1");
        filter.doFilter(first, new MockHttpServletResponse(), mock(FilterChain.class)); // exhausts 203.0.113.5

        // Same real address, a different spoofed header value: still exhausted.
        MockHttpServletRequest second = postRequest("203.0.113.5");
        second.addHeader("X-Forwarded-For", "9.9.9.9");
        second.addHeader("Forwarded", "for=8.8.8.8");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, mock(FilterChain.class));
        assertThat(secondResponse.getStatus()).isEqualTo(429);

        // A genuinely different real address — even replaying the first request's
        // spoofed header — gets its own, untouched bucket.
        MockHttpServletRequest third = postRequest("198.51.100.9");
        third.addHeader("X-Forwarded-For", "1.1.1.1");
        FilterChain thirdChain = mock(FilterChain.class);
        MockHttpServletResponse thirdResponse = new MockHttpServletResponse();
        filter.doFilter(third, thirdResponse, thirdChain);
        verify(thirdChain).doFilter(third, thirdResponse);
        assertThat(thirdResponse.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CORS preflight OPTIONS always passes through, even against an exhausted POST bucket")
    void optionsAlwaysPassesThrough() throws Exception {
        RateLimitFilter filter = newFilter(properties(1, true));
        filter.doFilter(postRequest("203.0.113.5"), new MockHttpServletResponse(), mock(FilterChain.class));

        MockHttpServletRequest options = new MockHttpServletRequest("OPTIONS", "/api/v1/scans");
        options.setRemoteAddr("203.0.113.5");
        FilterChain optionsChain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(options, response, optionsChain);

        verify(optionsChain).doFilter(options, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("the 429 body and headers never leak the client address or any forwarding-header value")
    void rejectionNeverLeaksIdentityOrHeaders() throws Exception {
        RateLimitFilter filter = newFilter(properties(1, true));
        String secretAddress = "203.0.113.77";
        MockHttpServletRequest first = postRequest(secretAddress);
        first.addHeader("X-Forwarded-For", "leak-me-not");
        filter.doFilter(first, new MockHttpServletResponse(), mock(FilterChain.class));

        MockHttpServletRequest second = postRequest(secretAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(second, response, mock(FilterChain.class));

        String body = response.getContentAsString();
        assertThat(body).doesNotContain(secretAddress, "leak-me-not", "X-Forwarded-For", "Forwarded");
        assertThat(response.getHeaderNames())
                .noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("ratelimit"));
    }

    @Test
    @DisplayName("Retry-After rounds up to the next whole second and is never less than one")
    void retryAfterRoundsUpAndNeverGoesBelowOne() {
        assertThat(RateLimitFilter.retryAfterSeconds(0)).isEqualTo(1);
        assertThat(RateLimitFilter.retryAfterSeconds(1)).isEqualTo(1);
        assertThat(RateLimitFilter.retryAfterSeconds(1_000_000_000L)).isEqualTo(1);
        assertThat(RateLimitFilter.retryAfterSeconds(1_000_000_001L)).isEqualTo(2);
        assertThat(RateLimitFilter.retryAfterSeconds(59_999_999_999L)).isEqualTo(60);
        assertThat(RateLimitFilter.retryAfterSeconds(60_000_000_000L)).isEqualTo(60);
    }

    private static RateLimitFilter newFilter(RateLimitProperties properties) {
        RateLimitBucketStore store = new RateLimitBucketStore(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        return new RateLimitFilter(properties, new RouteClassifier(), store);
    }

    private static RateLimitProperties properties(int capacity, boolean enabled) {
        RateLimitProperties.Bucket bucket = new RateLimitProperties.Bucket(capacity, 60);
        return new RateLimitProperties(
                enabled, bucket, bucket, new RateLimitProperties.Store(100, Duration.ofMinutes(10)));
    }

    private static MockHttpServletRequest postRequest(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/scans");
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
