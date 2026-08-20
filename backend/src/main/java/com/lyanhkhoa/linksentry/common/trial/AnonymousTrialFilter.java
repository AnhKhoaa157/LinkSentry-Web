package com.lyanhkhoa.linksentry.common.trial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lyanhkhoa.linksentry.common.api.ErrorResponse;
import com.lyanhkhoa.linksentry.license.security.LicensedDeviceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gates unlicensed {@code POST /api/v1/scans} to a rolling trial quota per
 * server-observed remote address, independent of {@code RateLimitFilter}'s general
 * anti-abuse control.
 *
 * <p>Placed immediately after {@code DeviceAuthenticationFilter} in the
 * security chain (see {@code SecurityConfig}) — after device authentication so
 * {@link SecurityContextHolder} already reflects a licensed device, if any, but before
 * {@code AnonymousAuthenticationFilter}, the controller, analysis, or persistence.
 * A licensed device is never gated here, and the general rate limiter still
 * applies to everyone regardless of this filter's outcome. A device with no active
 * license — pending activation, expired, or revoked — is gated exactly like a
 * caller with no device credential at all: this filter has no license-aware logic
 * of its own, because {@code DeviceAuthenticationFilter} only ever installs an
 * authentication for a currently licensed device.
 *
 * <p>Deliberately not a {@code @Component} for the same reason as
 * {@code RateLimitFilter}: constructed directly inside {@code SecurityConfig} and
 * given its own dedicated {@link ObjectMapper} rather than the application's bean,
 * since {@code SecurityFilterChain} beans resolve before {@code JacksonAutoConfiguration}
 * registers one.
 */
public final class AnonymousTrialFilter extends OncePerRequestFilter {

    private static final String MESSAGE = "Request a license to continue scanning.";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final RequestMatcher scanCreate =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/scans");

    private final AnonymousTrialProperties properties;
    private final AnonymousTrialStore store;

    public AnonymousTrialFilter(AnonymousTrialProperties properties, AnonymousTrialStore store) {
        this.properties = properties;
        this.store = store;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.enabled() || !scanCreate.matches(request) || isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (store.tryConsume(request.getRemoteAddr())) {
            filterChain.doFilter(request, response);
            return;
        }

        rejectWithTrialExhausted(response);
    }

    private static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof LicensedDeviceContext;
    }

    private void rejectWithTrialExhausted(HttpServletResponse response) throws IOException {
        String traceId = UUID.randomUUID().toString();
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // No remote address, count, reset time, quota state, or the submitted URL:
        // only the fixed safe envelope. See docs/SECURITY_BOUNDARY.md.
        OBJECT_MAPPER.writeValue(
                response.getWriter(), ErrorResponse.of("ANONYMOUS_TRIAL_EXHAUSTED", MESSAGE, traceId));
    }
}
