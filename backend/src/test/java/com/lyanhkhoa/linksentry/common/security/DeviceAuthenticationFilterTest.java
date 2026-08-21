package com.lyanhkhoa.linksentry.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lyanhkhoa.linksentry.license.application.DeviceService;
import com.lyanhkhoa.linksentry.license.security.LicensedDeviceContext;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Exercises {@link DeviceAuthenticationFilter} directly against a mocked {@link DeviceService}, the same
 * style as {@code AnonymousTrialFilterTest} uses for the filter immediately after it in the chain.
 */
class DeviceAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a licensed device's credential installs its identity and the request still reaches the chain")
    void licensedCredentialInstallsIdentity() throws Exception {
        DeviceService deviceService = mock(DeviceService.class);
        LicensedDeviceContext device = new LicensedDeviceContext(UUID.randomUUID(), UUID.randomUUID(), Instant.MAX);
        when(deviceService.authenticate("valid-credential")).thenReturn(Optional.of(device));
        DeviceAuthenticationFilter filter = new DeviceAuthenticationFilter(deviceService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/scans");
        request.addHeader("Authorization", "Device valid-credential");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(device);
    }

    @Test
    @DisplayName("a valid-looking credential belonging to an unlicensed (pending/expired/revoked) device installs nothing")
    void unlicensedCredentialInstallsNothing() throws Exception {
        DeviceService deviceService = mock(DeviceService.class);
        when(deviceService.authenticate("pending-credential")).thenReturn(Optional.empty());
        DeviceAuthenticationFilter filter = new DeviceAuthenticationFilter(deviceService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/scans");
        request.addHeader("Authorization", "Device pending-credential");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("a missing Authorization header never calls the device service and installs nothing")
    void missingHeaderNeverCallsService() throws Exception {
        DeviceService deviceService = mock(DeviceService.class);
        DeviceAuthenticationFilter filter = new DeviceAuthenticationFilter(deviceService);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/scans"), response, chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), eq(response));
        org.mockito.Mockito.verifyNoInteractions(deviceService);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("a different Authorization scheme (e.g. a leftover Bearer value) is never treated as a device credential")
    void wrongSchemeIsIgnored() throws Exception {
        DeviceService deviceService = mock(DeviceService.class);
        DeviceAuthenticationFilter filter = new DeviceAuthenticationFilter(deviceService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/scans");
        request.addHeader("Authorization", "Bearer some-old-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        org.mockito.Mockito.verifyNoInteractions(deviceService);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName(
            "a database failure resolving the credential on POST /api/v1/scans fails closed as 503 TRIAL_QUOTA_UNAVAILABLE, never a 500 or the chain")
    void databaseFailureOnScanCreateFailsClosed() throws Exception {
        DeviceService deviceService = mock(DeviceService.class);
        when(deviceService.authenticate("some-credential"))
                .thenThrow(new DataAccessResourceFailureException("simulated outage"));
        DeviceAuthenticationFilter filter = new DeviceAuthenticationFilter(deviceService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/scans");
        request.addHeader("Authorization", "Device some-credential");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(503);
        String body = response.getContentAsString();
        assertThat(body)
                .contains("\"code\":\"TRIAL_QUOTA_UNAVAILABLE\"")
                .contains("The trial scan quota is temporarily unavailable. Please try again shortly.")
                .doesNotContainIgnoringCase("simulated outage")
                .doesNotContainIgnoringCase("DataAccessResourceFailureException")
                .doesNotContain("some-credential", "/api/v1/scans");
        assertThat(response.getHeader("Retry-After")).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("a database failure resolving the credential on a route other than scan-create is not swallowed")
    void databaseFailureOnOtherRoutePropagates() {
        DeviceService deviceService = mock(DeviceService.class);
        CannotCreateTransactionException failure = new CannotCreateTransactionException("simulated outage");
        when(deviceService.authenticate("some-credential")).thenThrow(failure);
        DeviceAuthenticationFilter filter = new DeviceAuthenticationFilter(deviceService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/scans/some-id");
        request.addHeader("Authorization", "Device some-credential");
        MockHttpServletResponse response = new MockHttpServletResponse();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> filter.doFilter(request, response, mock(FilterChain.class)))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("the 503 body never leaks any header name hinting at rate limiting or a retry time")
    void databaseFailureResponseCarriesNoHintingHeaders() throws Exception {
        DeviceService deviceService = mock(DeviceService.class);
        when(deviceService.authenticate(eq("some-credential")))
                .thenThrow(new DataAccessResourceFailureException("simulated outage"));
        DeviceAuthenticationFilter filter = new DeviceAuthenticationFilter(deviceService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/scans");
        request.addHeader("Authorization", "Device some-credential");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeaderNames())
                .noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("retry")
                        || name.toLowerCase(Locale.ROOT).contains("ratelimit"));
    }
}
