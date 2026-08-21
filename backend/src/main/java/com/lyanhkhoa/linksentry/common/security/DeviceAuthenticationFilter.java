package com.lyanhkhoa.linksentry.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lyanhkhoa.linksentry.common.api.ErrorResponse;
import com.lyanhkhoa.linksentry.license.application.DeviceService;
import com.lyanhkhoa.linksentry.license.security.DeviceCredentialHeader;
import com.lyanhkhoa.linksentry.license.security.LicensedDeviceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.transaction.TransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads only the {@code Authorization: Device <credential>} header and installs a safe server identity —
 * but only when the presenting device is currently licensed.
 *
 * <p>A device with a valid credential but no active license (or an expired or revoked one) installs no
 * authentication here and falls through as an anonymous caller, exactly like a missing header. That is
 * deliberate: it is what makes a trial device receive only trial access, and it is why {@code
 * common.trial.AnonymousTrialFilter} — which runs immediately after this filter — needs no license-aware
 * logic of its own.
 *
 * <p>The installed authentication carries {@link #LICENSED_DEVICE_AUTHORITY} — never an empty authority
 * list — so {@code SecurityConfig}'s {@code hasAuthority(...)} checks can tell a licensed device apart
 * from every other authenticated principal (in particular {@code admin.domain.AdminIdentity}). Without a
 * distinct authority, any authenticated caller would satisfy a bare {@code .authenticated()} check on a
 * device-only route, which is exactly the cross-domain confusion this authority exists to prevent.
 *
 * <p><strong>ADR 0010's trial-admission path starts here, not at {@code AnonymousTrialFilter}.</strong>
 * {@link DeviceService#authenticate} already performs one database read for every request, licensed or
 * not, before the trial quota is ever reached. A database or persistence failure during that read on
 * {@code POST /api/v1/scans} is therefore part of the trial-admission path and fails closed here as the
 * same fixed {@code 503 TRIAL_QUOTA_UNAVAILABLE} response {@code AnonymousTrialFilter} uses for a failure
 * later in that path — never a generic {@code 500}, never a fallback authentication, and no raw URL,
 * credential, remote address, exception, or {@code Retry-After} disclosed. A failure on any other route
 * is outside this ADR's scope and propagates unchanged, matching this filter's prior behavior there.
 */
public final class DeviceAuthenticationFilter extends OncePerRequestFilter {

    /** Granted only to a currently licensed device's authentication. */
    public static final String LICENSED_DEVICE_AUTHORITY = "ROLE_LICENSED_DEVICE";

    private static final String QUOTA_UNAVAILABLE_MESSAGE =
            "The trial scan quota is temporarily unavailable. Please try again shortly.";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final RequestMatcher scanCreate =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/scans");

    private final DeviceService deviceService;

    public DeviceAuthenticationFilter(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            DeviceCredentialHeader.read(request)
                    .flatMap(deviceService::authenticate)
                    .ifPresent(this::installAuthentication);
        } catch (DataAccessException | TransactionException exception) {
            if (scanCreate.matches(request)) {
                rejectWithQuotaUnavailable(response);
                return;
            }
            throw exception;
        }
        filterChain.doFilter(request, response);
    }

    private void installAuthentication(LicensedDeviceContext device) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                device, null, List.of(new SimpleGrantedAuthority(LICENSED_DEVICE_AUTHORITY))));
    }

    private void rejectWithQuotaUnavailable(HttpServletResponse response) throws IOException {
        String traceId = UUID.randomUUID().toString();
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        OBJECT_MAPPER.writeValue(
                response.getWriter(),
                ErrorResponse.of("TRIAL_QUOTA_UNAVAILABLE", QUOTA_UNAVAILABLE_MESSAGE, traceId));
    }
}
