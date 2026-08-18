package com.lyanhkhoa.linksentry.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * Decides whether a request counts against a rate-limit bucket.
 *
 * <p>Scan and authentication endpoints are limited, matched by exact method and path.
 * Health, actuator health, Swagger/OpenAPI, static files, and CORS preflight are
 * never classified — there is no allow-list to keep in sync because everything
 * other than the matchers below simply falls through unmatched.
 */
@Component
public class RouteClassifier {

    private final RequestMatcher scanCreate =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/scans");

    // A single "*" segment matches exactly one path element (no "/"), mirroring the
    // controller's one-segment {scanId} path variable — it does not match the bare
    // collection path or a deeper path.
    private final RequestMatcher scanLookup =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/scans/*");

    private final RequestMatcher authWrite =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/*");

    private final RequestMatcher authSession =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/auth/session");

    Optional<RateLimitedRoute> classify(HttpServletRequest request) {
        if (scanCreate.matches(request)) {
            return Optional.of(RateLimitedRoute.SCAN_CREATE);
        }
        if (scanLookup.matches(request)) {
            return Optional.of(RateLimitedRoute.SCAN_LOOKUP);
        }
        if (authWrite.matches(request) || authSession.matches(request)) {
            return Optional.of(RateLimitedRoute.AUTH);
        }
        return Optional.empty();
    }
}
