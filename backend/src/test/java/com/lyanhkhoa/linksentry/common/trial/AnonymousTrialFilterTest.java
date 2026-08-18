package com.lyanhkhoa.linksentry.common.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lyanhkhoa.linksentry.auth.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Exercises {@link AnonymousTrialFilter} end to end against a real
 * {@link AnonymousTrialStore}, using Spring's servlet mocks for the request and
 * response so assertions read exactly what a real client would receive — the same
 * approach as {@code RateLimitFilterTest}.
 */
class AnonymousTrialFilterTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("the first maxScans anonymous requests pass through untouched")
    void withinQuotaPassesThrough() throws Exception {
        AnonymousTrialFilter filter = newFilter(properties(3, true));

        for (int i = 0; i < 3; i++) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletRequest request = anonymousPostRequest("203.0.113.5");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("the fourth anonymous request is rejected with a safe 429 and never reaches the chain")
    void fourthAnonymousRequestIsRejected() throws Exception {
        AnonymousTrialFilter filter = newFilter(properties(3, true));
        exhaust(filter, "203.0.113.5", 3);

        FilterChain fourthChain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(anonymousPostRequest("203.0.113.5"), response, fourthChain);

        // No chain interaction: the controller, analyzer, and persistence are never reached.
        verifyNoInteractions(fourthChain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"code\":\"ANONYMOUS_TRIAL_EXHAUSTED\"")
                .contains("Sign in to continue scanning.")
                .contains("\"traceId\"");
    }

    @Test
    @DisplayName("IPv4 and IPv6 hold independent trial quotas")
    void ipv4AndIpv6AreIndependent() throws Exception {
        AnonymousTrialFilter filter = newFilter(properties(1, true));
        exhaust(filter, "203.0.113.5", 1);

        FilterChain ipv6Chain = mock(FilterChain.class);
        MockHttpServletRequest ipv6Request = anonymousPostRequest("2001:db8::1");
        MockHttpServletResponse ipv6Response = new MockHttpServletResponse();
        filter.doFilter(ipv6Request, ipv6Response, ipv6Chain);

        verify(ipv6Chain).doFilter(ipv6Request, ipv6Response);
        assertThat(ipv6Response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("an authenticated caller bypasses the guard even from an already-exhausted address")
    void authenticatedCallerBypassesGuard() throws Exception {
        AnonymousTrialFilter filter = newFilter(properties(1, true));
        exhaust(filter, "203.0.113.5", 1);

        installAuthenticatedUser();
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = anonymousPostRequest("203.0.113.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("disabled mode never rejects, even once an address would otherwise be exhausted")
    void disabledModeAlwaysPassesThrough() throws Exception {
        AnonymousTrialFilter filter = newFilter(properties(1, false));

        filter.doFilter(anonymousPostRequest("203.0.113.5"), new MockHttpServletResponse(), mock(FilterChain.class));

        FilterChain secondChain = mock(FilterChain.class);
        MockHttpServletRequest secondRequest = anonymousPostRequest("203.0.113.5");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, secondChain);

        verify(secondChain).doFilter(secondRequest, secondResponse);
        assertThat(secondResponse.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CORS preflight OPTIONS on the scan route is never gated, even against an exhausted quota")
    void optionsPreflightAlwaysPassesThrough() throws Exception {
        AnonymousTrialFilter filter = newFilter(properties(1, true));
        exhaust(filter, "203.0.113.5", 1);

        MockHttpServletRequest options = new MockHttpServletRequest("OPTIONS", "/api/v1/scans");
        options.setRemoteAddr("203.0.113.5");
        FilterChain optionsChain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(options, response, optionsChain);

        verify(optionsChain).doFilter(options, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("a route other than POST /api/v1/scans is never gated, even against an exhausted quota")
    void unrelatedRouteIsNeverGated() throws Exception {
        AnonymousTrialFilter filter = newFilter(properties(1, true));
        exhaust(filter, "203.0.113.5", 1);

        MockHttpServletRequest lookup = new MockHttpServletRequest("GET", "/api/v1/scans/some-id");
        lookup.setRemoteAddr("203.0.113.5");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(lookup, response, chain);

        verify(chain).doFilter(lookup, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("identity comes only from getRemoteAddr; spoofed forwarding headers change nothing")
    void identityIgnoresForwardingHeaders() throws Exception {
        AnonymousTrialFilter filter = newFilter(properties(1, true));

        MockHttpServletRequest first = anonymousPostRequest("203.0.113.5");
        first.addHeader("X-Forwarded-For", "1.1.1.1");
        filter.doFilter(first, new MockHttpServletResponse(), mock(FilterChain.class)); // exhausts 203.0.113.5

        MockHttpServletRequest second = anonymousPostRequest("203.0.113.5");
        second.addHeader("X-Forwarded-For", "9.9.9.9");
        second.addHeader("Forwarded", "for=8.8.8.8");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, mock(FilterChain.class));
        assertThat(secondResponse.getStatus()).isEqualTo(429);

        // A genuinely different real address — even replaying the first request's
        // spoofed header — gets its own, untouched quota.
        MockHttpServletRequest third = anonymousPostRequest("198.51.100.9");
        third.addHeader("X-Forwarded-For", "1.1.1.1");
        FilterChain thirdChain = mock(FilterChain.class);
        MockHttpServletResponse thirdResponse = new MockHttpServletResponse();
        filter.doFilter(third, thirdResponse, thirdChain);
        verify(thirdChain).doFilter(third, thirdResponse);
        assertThat(thirdResponse.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("the 429 body never leaks the remote address, a count, a reset time, or forwarding-header values")
    void rejectionNeverLeaksIdentityOrQuotaState() throws Exception {
        AnonymousTrialFilter filter = newFilter(properties(1, true));
        String secretAddress = "203.0.113.77";
        MockHttpServletRequest first = anonymousPostRequest(secretAddress);
        first.addHeader("X-Forwarded-For", "leak-me-not");
        filter.doFilter(first, new MockHttpServletResponse(), mock(FilterChain.class));

        MockHttpServletRequest second = anonymousPostRequest(secretAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(second, response, mock(FilterChain.class));

        String body = response.getContentAsString();
        assertThat(body)
                .doesNotContain(secretAddress, "leak-me-not", "X-Forwarded-For", "Forwarded")
                .doesNotContainIgnoringCase("remaining")
                .doesNotContainIgnoringCase("reset")
                .doesNotContainIgnoringCase("quota");
        assertThat(response.getHeaderNames())
                .noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("trial")
                        || name.toLowerCase(Locale.ROOT).contains("ratelimit"));
    }

    private static void exhaust(AnonymousTrialFilter filter, String remoteAddr, int maxScans) throws Exception {
        for (int i = 0; i < maxScans; i++) {
            filter.doFilter(anonymousPostRequest(remoteAddr), new MockHttpServletResponse(), mock(FilterChain.class));
        }
    }

    private static void installAuthenticatedUser() {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(), "person@example.com", UUID.randomUUID(), NOW.plusSeconds(3600));
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
    }

    private static AnonymousTrialFilter newFilter(AnonymousTrialProperties properties) {
        AnonymousTrialStore store = new AnonymousTrialStore(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        return new AnonymousTrialFilter(properties, store);
    }

    private static AnonymousTrialProperties properties(int maxScans, boolean enabled) {
        return new AnonymousTrialProperties(
                enabled, maxScans, Duration.ofHours(24), new AnonymousTrialProperties.Store(100, Duration.ofHours(24)));
    }

    private static MockHttpServletRequest anonymousPostRequest(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/scans");
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
