package com.lyanhkhoa.linksentry.common.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lyanhkhoa.linksentry.common.trial.persistence.DeviceTrialQuotaService;
import com.lyanhkhoa.linksentry.license.security.LicensedDeviceContext;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Exercises {@link AnonymousTrialFilter} against a mocked {@link DeviceTrialQuotaService}, the same
 * style {@code DeviceAuthenticationFilterTest} uses for the filter immediately before it in the
 * chain. Real device-resolution and quota-persistence behavior is proven end-to-end against
 * PostgreSQL by {@code AnonymousTrialPostgresIntegrationTest}.
 */
class AnonymousTrialFilterTest {

    private static final Instant NOW = Instant.parse("2026-08-21T12:00:00Z");
    private static final UUID DEVICE_ID = UUID.randomUUID();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a resolved device still under quota passes through untouched")
    void admittedRequestPassesThrough() throws Exception {
        DeviceTrialQuotaService quotaService = mock(DeviceTrialQuotaService.class);
        when(quotaService.resolveDeviceId("valid-credential")).thenReturn(Optional.of(DEVICE_ID));
        when(quotaService.tryAdmit(eq(DEVICE_ID), any())).thenReturn(true);
        AnonymousTrialFilter filter = newFilter(properties(3, true), quotaService);
        MockHttpServletRequest request = trialRequest("valid-credential");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("a resolved device beyond quota is rejected with a safe 429 and never reaches the chain")
    void exhaustedDeviceIsRejected() throws Exception {
        DeviceTrialQuotaService quotaService = mock(DeviceTrialQuotaService.class);
        when(quotaService.resolveDeviceId("exhausted-credential")).thenReturn(Optional.of(DEVICE_ID));
        when(quotaService.tryAdmit(eq(DEVICE_ID), any())).thenReturn(false);
        AnonymousTrialFilter filter = newFilter(properties(3, true), quotaService);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(trialRequest("exhausted-credential"), response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"ANONYMOUS_TRIAL_EXHAUSTED\"")
                .contains("Request a license to continue scanning.")
                .contains("\"traceId\"");
    }

    @Test
    @DisplayName("no Authorization header at all gets the fixed 401 TRIAL_DEVICE_REQUIRED and never calls the quota service")
    void missingCredentialIsRejected() throws Exception {
        DeviceTrialQuotaService quotaService = mock(DeviceTrialQuotaService.class);
        AnonymousTrialFilter filter = newFilter(properties(3, true), quotaService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/scans");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        verifyNoInteractions(quotaService);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"TRIAL_DEVICE_REQUIRED\"")
                .contains("A valid device credential is required to use the trial.");
    }

    @Test
    @DisplayName("a malformed Authorization scheme gets the identical 401 TRIAL_DEVICE_REQUIRED")
    void malformedCredentialIsRejected() throws Exception {
        DeviceTrialQuotaService quotaService = mock(DeviceTrialQuotaService.class);
        AnonymousTrialFilter filter = newFilter(properties(3, true), quotaService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/scans");
        request.addHeader("Authorization", "Bearer leftover-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        verifyNoInteractions(quotaService);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"TRIAL_DEVICE_REQUIRED\"");
    }

    @Test
    @DisplayName("a credential matching no known device gets the identical 401 TRIAL_DEVICE_REQUIRED")
    void unknownCredentialIsRejected() throws Exception {
        DeviceTrialQuotaService quotaService = mock(DeviceTrialQuotaService.class);
        when(quotaService.resolveDeviceId("unknown-credential")).thenReturn(Optional.empty());
        AnonymousTrialFilter filter = newFilter(properties(3, true), quotaService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(trialRequest("unknown-credential"), response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"TRIAL_DEVICE_REQUIRED\"");
    }

    @Test
    @DisplayName("a licensed device bypasses the guard without ever calling the quota service")
    void licensedDeviceBypassesGuard() throws Exception {
        DeviceTrialQuotaService quotaService = mock(DeviceTrialQuotaService.class);
        installLicensedDevice();
        AnonymousTrialFilter filter = newFilter(properties(1, true), quotaService);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = trialRequest("some-credential");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(quotaService);
    }

    @Test
    @DisplayName("disabled mode never gates, even without a credential")
    void disabledModeAlwaysPassesThrough() throws Exception {
        DeviceTrialQuotaService quotaService = mock(DeviceTrialQuotaService.class);
        AnonymousTrialFilter filter = newFilter(properties(1, false), quotaService);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/scans");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(quotaService);
    }

    @Test
    @DisplayName("a route other than POST /api/v1/scans is never gated")
    void unrelatedRouteIsNeverGated() throws Exception {
        DeviceTrialQuotaService quotaService = mock(DeviceTrialQuotaService.class);
        AnonymousTrialFilter filter = newFilter(properties(1, true), quotaService);
        MockHttpServletRequest lookup = new MockHttpServletRequest("GET", "/api/v1/scans/some-id");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(lookup, response, chain);

        verify(chain).doFilter(lookup, response);
        verifyNoInteractions(quotaService);
    }

    @Test
    @DisplayName("a persistence failure resolving the device credential fails closed as 503 TRIAL_QUOTA_UNAVAILABLE, never a 500 or admission")
    void persistenceFailureDuringResolutionFailsClosed() throws Exception {
        DeviceTrialQuotaService quotaService = mock(DeviceTrialQuotaService.class);
        when(quotaService.resolveDeviceId("some-credential"))
                .thenThrow(new DataAccessResourceFailureException("simulated outage"));
        AnonymousTrialFilter filter = newFilter(properties(3, true), quotaService);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(trialRequest("some-credential"), response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(503);
        String body = response.getContentAsString();
        assertThat(body)
                .contains("\"code\":\"TRIAL_QUOTA_UNAVAILABLE\"")
                .contains("The trial scan quota is temporarily unavailable. Please try again shortly.")
                .doesNotContainIgnoringCase("simulated outage")
                .doesNotContainIgnoringCase("DataAccessResourceFailureException")
                .doesNotContain("some-credential");
        assertThat(response.getHeader("Retry-After")).isNull();
    }

    @Test
    @DisplayName("a persistence failure during the admit transaction also fails closed as 503 TRIAL_QUOTA_UNAVAILABLE")
    void persistenceFailureDuringAdmitFailsClosed() throws Exception {
        DeviceTrialQuotaService quotaService = mock(DeviceTrialQuotaService.class);
        when(quotaService.resolveDeviceId("some-credential")).thenReturn(Optional.of(DEVICE_ID));
        when(quotaService.tryAdmit(eq(DEVICE_ID), any()))
                .thenThrow(new CannotCreateTransactionException("simulated outage"));
        AnonymousTrialFilter filter = newFilter(properties(3, true), quotaService);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(trialRequest("some-credential"), response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("\"code\":\"TRIAL_QUOTA_UNAVAILABLE\"");
    }

    @Test
    @DisplayName("no rejection body ever leaks a device id, credential, remote address, or Retry-After")
    void rejectionsNeverLeakSensitiveData() throws Exception {
        DeviceTrialQuotaService quotaService = mock(DeviceTrialQuotaService.class);
        when(quotaService.resolveDeviceId("secret-credential-value")).thenReturn(Optional.of(DEVICE_ID));
        when(quotaService.tryAdmit(eq(DEVICE_ID), any())).thenReturn(false);
        AnonymousTrialFilter filter = newFilter(properties(3, true), quotaService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(trialRequest("secret-credential-value"), response, mock(FilterChain.class));

        String body = response.getContentAsString();
        assertThat(body)
                .doesNotContain(DEVICE_ID.toString(), "secret-credential-value")
                .doesNotContainIgnoringCase("remaining")
                .doesNotContainIgnoringCase("reset");
        assertThat(response.getHeaderNames())
                .noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("retry")
                        || name.toLowerCase(Locale.ROOT).contains("trial"));
    }

    private static void installLicensedDevice() {
        LicensedDeviceContext device = new LicensedDeviceContext(UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(3600));
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(device, null, List.of()));
    }

    private static AnonymousTrialFilter newFilter(AnonymousTrialProperties properties, DeviceTrialQuotaService quotaService) {
        return new AnonymousTrialFilter(properties, quotaService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AnonymousTrialProperties properties(int maxScans, boolean enabled) {
        return new AnonymousTrialProperties(enabled, maxScans, Duration.ofHours(24));
    }

    private static MockHttpServletRequest trialRequest(String credential) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/scans");
        request.addHeader("Authorization", "Device " + credential);
        return request;
    }
}
