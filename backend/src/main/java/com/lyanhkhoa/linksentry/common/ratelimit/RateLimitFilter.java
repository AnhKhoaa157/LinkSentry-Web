package com.lyanhkhoa.linksentry.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lyanhkhoa.linksentry.common.api.ErrorResponse;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces the configured per-address token buckets before a request reaches
 * {@code DispatcherServlet}.
 *
 * <p>Wired into the Spring Security chain immediately after {@code CorsFilter} (see
 * {@code common.security.SecurityConfig}), which means both edge cases resolve
 * without any special-casing here: a disallowed origin is already rejected by CORS
 * before this filter ever runs, and a CORS preflight {@code OPTIONS} is fully
 * answered by {@code CorsFilter} and never reaches this filter either. Every request
 * that does reach it consumes a token before the controller sees it, so validation
 * outcome — 2xx, 4xx, or 5xx — never changes what was already charged.
 *
 * <p>Deliberately not a {@code @Component}: a {@code Filter} bean sitting in the
 * application context gets auto-registered a second time as a container filter by
 * Spring Boot, independently of where {@code addFilterAfter} placed it in the
 * security chain. This class is instead constructed directly inside
 * {@code SecurityConfig}'s {@code SecurityFilterChain @Bean} method.
 *
 * <p>Serializes its own fixed, tiny response body with a dedicated {@link ObjectMapper}
 * rather than injecting the application's bean: {@code SecurityFilterChain} beans are
 * resolved earlier than {@code JacksonAutoConfiguration} registers one, so an eager
 * dependency here fails context startup, and this response never needs the app's wider
 * Jackson customisation anyway.
 */
public final class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String RATE_LIMITED_MESSAGE = "Too many requests. Please slow down and try again shortly.";

    // Matches the ISO-8601 instant formatting the application's own ObjectMapper
    // produces for every other error response, so `timestamp` is uniform across the
    // whole error envelope contract regardless of which code path writes it.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final RateLimitProperties properties;
    private final RouteClassifier routeClassifier;
    private final RateLimitBucketStore bucketStore;

    public RateLimitFilter(
            RateLimitProperties properties, RouteClassifier routeClassifier, RateLimitBucketStore bucketStore) {
        this.properties = properties;
        this.routeClassifier = routeClassifier;
        this.bucketStore = bucketStore;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<RateLimitedRoute> route = routeClassifier.classify(request);
        if (route.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = bucketStore.bucketFor(request.getRemoteAddr(), route.get());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        rejectWithRateLimit(response, route.get(), probe.getNanosToWaitForRefill());
    }

    private void rejectWithRateLimit(HttpServletResponse response, RateLimitedRoute route, long nanosToWaitForRefill)
            throws IOException {
        String traceId = UUID.randomUUID().toString();
        long retryAfterSeconds = retryAfterSeconds(nanosToWaitForRefill);
        // Route and traceId only: never the client address, the bucket key, or the request path.
        log.info("Rate limit exceeded [traceId={}, route={}]", traceId, route);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        OBJECT_MAPPER.writeValue(
                response.getWriter(), ErrorResponse.of("RATE_LIMITED", RATE_LIMITED_MESSAGE, traceId));
    }

    /** Rounds up to the next whole second, and never returns less than one. */
    static long retryAfterSeconds(long nanosToWaitForRefill) {
        long wholeSecondsRoundedUp = (nanosToWaitForRefill + 999_999_999L) / 1_000_000_000L;
        return Math.max(1L, wholeSecondsRoundedUp);
    }
}
