package com.lyanhkhoa.linksentry.common.trial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lyanhkhoa.linksentry.common.api.ErrorResponse;
import com.lyanhkhoa.linksentry.common.trial.persistence.DeviceTrialQuotaService;
import com.lyanhkhoa.linksentry.license.security.DeviceCredentialHeader;
import com.lyanhkhoa.linksentry.license.security.LicensedDeviceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.transaction.TransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gates unlicensed {@code POST /api/v1/scans} to a persistent, device-scoped rolling trial quota
 * (ADR 0010), independent of {@code RateLimitFilter}'s general anti-abuse control.
 *
 * <p>Placed immediately after {@code DeviceAuthenticationFilter} in the security chain (see
 * {@code SecurityConfig}) — after device authentication so {@link SecurityContextHolder} already
 * reflects a licensed device, if any, but before {@code AnonymousAuthenticationFilter}, the
 * controller, analysis, or persistence. A licensed device is never gated here, and the general rate
 * limiter still applies to everyone regardless of this filter's outcome.
 *
 * <p>Every other caller must present a credential resolving to <em>some</em> known device
 * installation — pending, expired, or revoked all qualify, the same distinction ADR 0008 already
 * draws between a device's credential and its entitlement. A missing, malformed, or unrecognised
 * credential all return the identical {@code 401 TRIAL_DEVICE_REQUIRED}, so the response never
 * discloses which of the three applied. A resolved device is admitted at most {@code maxScans}
 * scans per rolling, inclusive {@code window}, persisted by device id — an event exactly {@code
 * window} old still counts; only one strictly older is pruned, the same boundary rule the retired
 * in-memory store used. A database or persistence failure anywhere in this admission path
 * (credential resolution, the row lock, prune/count/insert, or commit) fails closed as the fixed
 * {@code 503 TRIAL_QUOTA_UNAVAILABLE} — never a fallback admission, never a generic {@code 500}.
 *
 * <p>Deliberately not a {@code @Component} for the same reason as {@code RateLimitFilter}:
 * constructed directly inside {@code SecurityConfig} and given its own dedicated {@link
 * ObjectMapper} rather than the application's bean, since {@code SecurityFilterChain} beans resolve
 * before {@code JacksonAutoConfiguration} registers one.
 */
public final class AnonymousTrialFilter extends OncePerRequestFilter {

    private static final String EXHAUSTED_MESSAGE = "Request a license to continue scanning.";
    private static final String DEVICE_REQUIRED_MESSAGE = "A valid device credential is required to use the trial.";
    private static final String QUOTA_UNAVAILABLE_MESSAGE =
            "The trial scan quota is temporarily unavailable. Please try again shortly.";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final RequestMatcher scanCreate =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/scans");

    private final AnonymousTrialProperties properties;
    private final DeviceTrialQuotaService quotaService;
    private final Clock clock;

    public AnonymousTrialFilter(AnonymousTrialProperties properties, DeviceTrialQuotaService quotaService, Clock clock) {
        this.properties = properties;
        this.quotaService = quotaService;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.enabled() || !scanCreate.matches(request) || isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<String> rawCredential = DeviceCredentialHeader.read(request);
        if (rawCredential.isEmpty()) {
            rejectWithDeviceRequired(response);
            return;
        }

        boolean admitted;
        try {
            Optional<UUID> deviceId = quotaService.resolveDeviceId(rawCredential.get());
            if (deviceId.isEmpty()) {
                rejectWithDeviceRequired(response);
                return;
            }
            admitted = quotaService.tryAdmit(deviceId.get(), Instant.now(clock));
        } catch (DataAccessException | TransactionException exception) {
            rejectWithQuotaUnavailable(response);
            return;
        }

        if (admitted) {
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
        // No remote address, device id, count, reset time, quota state, or the submitted URL:
        // only the fixed safe envelope. See docs/SECURITY_BOUNDARY.md.
        writeError(response, HttpStatus.TOO_MANY_REQUESTS, "ANONYMOUS_TRIAL_EXHAUSTED", EXHAUSTED_MESSAGE);
    }

    private void rejectWithDeviceRequired(HttpServletResponse response) throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED, "TRIAL_DEVICE_REQUIRED", DEVICE_REQUIRED_MESSAGE);
    }

    private void rejectWithQuotaUnavailable(HttpServletResponse response) throws IOException {
        // Never the raw exception, a URL, a credential, a remote address, or Retry-After: the
        // outage duration is not knowable. See ADR 0010's "Failure behavior".
        writeError(response, HttpStatus.SERVICE_UNAVAILABLE, "TRIAL_QUOTA_UNAVAILABLE", QUOTA_UNAVAILABLE_MESSAGE);
    }

    private static void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        String traceId = UUID.randomUUID().toString();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        OBJECT_MAPPER.writeValue(response.getWriter(), ErrorResponse.of(code, message, traceId));
    }
}
