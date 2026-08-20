package com.lyanhkhoa.linksentry.common.security;

import com.lyanhkhoa.linksentry.license.application.DeviceService;
import com.lyanhkhoa.linksentry.license.security.DeviceCredentialHeader;
import com.lyanhkhoa.linksentry.license.security.LicensedDeviceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
 */
public final class DeviceAuthenticationFilter extends OncePerRequestFilter {

    /** Granted only to a currently licensed device's authentication. */
    public static final String LICENSED_DEVICE_AUTHORITY = "ROLE_LICENSED_DEVICE";

    private final DeviceService deviceService;

    public DeviceAuthenticationFilter(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        DeviceCredentialHeader.read(request)
                .flatMap(deviceService::authenticate)
                .ifPresent(this::installAuthentication);
        filterChain.doFilter(request, response);
    }

    private void installAuthentication(LicensedDeviceContext device) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                device, null, List.of(new SimpleGrantedAuthority(LICENSED_DEVICE_AUTHORITY))));
    }
}
