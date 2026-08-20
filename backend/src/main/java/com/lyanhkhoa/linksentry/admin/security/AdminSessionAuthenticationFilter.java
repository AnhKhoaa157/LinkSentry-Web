package com.lyanhkhoa.linksentry.admin.security;

import com.lyanhkhoa.linksentry.admin.application.AdminAuthService;
import com.lyanhkhoa.linksentry.admin.domain.AdminIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads only an {@code Authorization: Bearer <token>} header and installs a safe administrator
 * identity. Runs on every request, the same as the former end-user bearer filter did; {@code
 * common.security.SecurityConfig}'s {@code authorizeHttpRequests} decides which routes actually
 * require it. Coexists with {@code license.security.DeviceCredentialHeader}'s {@code Authorization:
 * Device <credential>} scheme on the same header — the two prefixes never collide — and never
 * touches {@code common.security.AdminApiKeyFilter}'s separate {@code X-Admin-Api-Key} header.
 *
 * <p>The installed authentication carries {@link #ADMIN_AUTHORITY} — never an empty authority list —
 * so {@code SecurityConfig}'s {@code hasAuthority(...)} checks can tell an administrator apart from
 * every other authenticated principal (in particular {@code license.security.LicensedDeviceContext}).
 * A licensed device's bearer session must never satisfy an admin-only route, and this authority is
 * what makes that a structural guarantee rather than an implementation detail.
 */
public final class AdminSessionAuthenticationFilter extends OncePerRequestFilter {

    /** Granted only to a currently valid administrator session's authentication. */
    public static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private static final String BEARER_PREFIX = "Bearer ";
    private final AdminAuthService adminAuthService;

    public AdminSessionAuthenticationFilter(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String rawToken = authorization.substring(BEARER_PREFIX.length()).trim();
            if (!rawToken.isEmpty() && rawToken.length() <= 256) {
                adminAuthService.authenticate(rawToken).ifPresent(this::installAuthentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void installAuthentication(AdminIdentity adminIdentity) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                adminIdentity, null, List.of(new SimpleGrantedAuthority(ADMIN_AUTHORITY))));
    }
}
