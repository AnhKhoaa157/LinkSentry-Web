package com.lyanhkhoa.linksentry.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lyanhkhoa.linksentry.admin.security.AdminSessionAuthenticationFilter;
import com.lyanhkhoa.linksentry.common.api.ErrorResponse;
import com.lyanhkhoa.linksentry.common.config.AdminProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gates every {@code /api/v1/admin/**} route behind either a valid browser administrator session or the
 * {@code ADMIN_API_KEY} configured only in backend environment configuration ({@link AdminProperties}). The
 * frontend and extension never receive or send that key; the operator fallback remains available for an
 * operator's own {@code curl} calls.
 *
 * <p>Deliberately not a {@code @Component}, for the same reason as {@code RateLimitFilter} and {@code
 * AnonymousTrialFilter}: constructed directly inside {@code SecurityConfig} so it is not also
 * auto-registered a second time as a container filter, and given its own dedicated {@link ObjectMapper}
 * since a {@code SecurityFilterChain} bean resolves before {@code JacksonAutoConfiguration} registers one.
 *
 * <p>An unconfigured (blank) {@code ADMIN_API_KEY} rejects admin requests that do not carry a valid
 * administrator session rather than allowing them: there is no configured value a presented key could
 * ever legitimately match, so "unset" means "unreachable" through the operator path, never "open."
 */
public final class AdminApiKeyFilter extends OncePerRequestFilter {

    private static final String ADMIN_KEY_HEADER = "X-Admin-Api-Key";
    private static final String MESSAGE = "Authentication is required to access this resource.";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final RequestMatcher adminRoutes = PathPatternRequestMatcher.withDefaults().matcher("/api/v1/admin/**");
    private final AdminProperties adminProperties;

    public AdminApiKeyFilter(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!adminRoutes.matches(request) || isAuthorized(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        rejectUnauthorized(response);
    }

    private boolean isAuthorized(HttpServletRequest request) {
        if (hasAdminSession()) {
            return true;
        }
        String configuredKey = adminProperties.apiKey();
        String presentedKey = request.getHeader(ADMIN_KEY_HEADER);
        if (configuredKey == null || configuredKey.isBlank() || presentedKey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredKey.getBytes(StandardCharsets.UTF_8), presentedKey.getBytes(StandardCharsets.UTF_8));
    }

    private boolean hasAdminSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> AdminSessionAuthenticationFilter.ADMIN_AUTHORITY
                                .equals(authority.getAuthority()));
    }

    private void rejectUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // No detail beyond the fixed safe message: never which part of the key check failed, and
        // never the configured or presented key.
        OBJECT_MAPPER.writeValue(
                response.getWriter(), ErrorResponse.of("UNAUTHORIZED", MESSAGE, UUID.randomUUID().toString()));
    }
}
