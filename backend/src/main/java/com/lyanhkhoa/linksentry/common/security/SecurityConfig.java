package com.lyanhkhoa.linksentry.common.security;

import com.lyanhkhoa.linksentry.admin.application.AdminAuthService;
import com.lyanhkhoa.linksentry.admin.security.AdminSessionAuthenticationFilter;
import com.lyanhkhoa.linksentry.common.config.AdminProperties;
import com.lyanhkhoa.linksentry.common.config.CorsProperties;
import com.lyanhkhoa.linksentry.common.ratelimit.RateLimitBucketStore;
import com.lyanhkhoa.linksentry.common.ratelimit.RateLimitFilter;
import com.lyanhkhoa.linksentry.common.ratelimit.RateLimitProperties;
import com.lyanhkhoa.linksentry.common.ratelimit.RouteClassifier;
import com.lyanhkhoa.linksentry.common.trial.AnonymousTrialFilter;
import com.lyanhkhoa.linksentry.common.trial.AnonymousTrialProperties;
import com.lyanhkhoa.linksentry.common.trial.AnonymousTrialStore;
import com.lyanhkhoa.linksentry.license.application.DeviceService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Stateless security baseline for public scans, device bootstrap/status, licensed-only routes,
 * backend-only admin routes, and the browser-facing admin console.
 *
 * <p>URL analysis remains public for one-off scans, device bootstrap/status are always public (a device
 * must be able to ask "what is my state?" before it has anything to authenticate with), retained-history
 * and AI-explanation routes require a currently licensed device, while {@code /api/v1/admin/**} accepts
 * either an administrator session or {@code ADMIN_API_KEY}, and {@code /api/v1/admin-auth/session}/{@code
 * logout} require an administrator's own bearer session (see {@code admin.security}) — a wholly separate,
 * human-login mechanism from {@code ADMIN_API_KEY}. The API never uses cookies:
 *
 * <p><strong>Authority, not just authentication, gates the two authenticated route families above.</strong>
 * {@link DeviceAuthenticationFilter} and {@link com.lyanhkhoa.linksentry.admin.security.AdminSessionAuthenticationFilter}
 * both install a real, non-anonymous {@code Authentication}, so a bare {@code .authenticated()} check
 * cannot tell a licensed device's session apart from an administrator's — either would satisfy it. Each
 * filter instead grants its own distinct {@code GrantedAuthority} ({@link DeviceAuthenticationFilter#LICENSED_DEVICE_AUTHORITY}
 * and {@link com.lyanhkhoa.linksentry.admin.security.AdminSessionAuthenticationFilter#ADMIN_AUTHORITY}), and
 * {@code authorizeHttpRequests} below matches on that authority, not merely on "is authenticated." A
 * request that is authenticated but holds the wrong authority is rejected with a clean {@code 403} via
 * {@link ApiAccessDeniedHandler} — never silently downgraded to anonymous access and never a stack trace.
 * A request with no valid credential at all still gets the existing {@code 401} via
 * {@link ApiAuthenticationEntryPoint}, because {@code ExceptionTranslationFilter} recognises the
 * anonymous token installed for every unauthenticated request and routes it to the entry point instead
 * of the access-denied handler.
 *
 * <ul>
 *   <li><strong>No session.</strong> Nothing is stored between requests, so no
 *       session fixation or session-hijacking surface exists.
 *   <li><strong>CSRF disabled.</strong> Safe <em>only because</em> the API is
 *       cookie-free and stateless — a CSRF attack needs an ambient credential the
 *       browser attaches automatically. If a cookie-based session is ever
 *       introduced, CSRF protection must be re-enabled in the same commit.
 *   <li><strong>No form login or HTTP Basic.</strong> Prevents Spring Security's
 *       defaults from turning a missing configuration into a browser login prompt.
 *   <li><strong>Default response headers kept.</strong> Including
 *       {@code X-Frame-Options: DENY} and {@code X-Content-Type-Options: nosniff}.
 *   <li><strong>Rate limited.</strong> {@link RateLimitFilter} sits immediately after
 *       {@link CorsFilter}, so a disallowed origin never reaches it and an allowed
 *       origin's response — including a {@code 429} — keeps its CORS headers. See
 *       {@code common.ratelimit} for the single-instance, in-memory token buckets.
 *   <li><strong>Admin credentials checked early.</strong> {@link AdminSessionAuthenticationFilter} runs
 *       before {@link AdminApiKeyFilter}, so the admin route gate can recognize a valid browser session
 *       while every request is still rate limited before either credential is evaluated.
 *   <li><strong>Anonymous trial quota.</strong> {@link AnonymousTrialFilter} sits
 *       immediately after {@link DeviceAuthenticationFilter}, so it can tell a
 *       licensed device apart from every other caller and never gates the former.
 *       It is independent of rate limiting — both apply to every anonymous scan. See
 *       {@code common.trial}.
 * </ul>
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            UrlBasedCorsConfigurationSource corsConfigurationSource,
            RateLimitProperties rateLimitProperties,
            RouteClassifier rateLimitRouteClassifier,
            RateLimitBucketStore rateLimitBucketStore,
            AdminProperties adminProperties,
            AdminAuthService adminAuthService,
            DeviceService deviceService,
            AnonymousTrialProperties anonymousTrialProperties,
            AnonymousTrialStore anonymousTrialStore)
            throws Exception {
        RateLimitFilter rateLimitFilter =
                new RateLimitFilter(rateLimitProperties, rateLimitRouteClassifier, rateLimitBucketStore);
        AdminApiKeyFilter adminApiKeyFilter = new AdminApiKeyFilter(adminProperties);
        AdminSessionAuthenticationFilter adminSessionAuthenticationFilter =
                new AdminSessionAuthenticationFilter(adminAuthService);
        DeviceAuthenticationFilter deviceAuthenticationFilter = new DeviceAuthenticationFilter(deviceService);
        AnonymousTrialFilter anonymousTrialFilter =
                new AnonymousTrialFilter(anonymousTrialProperties, anonymousTrialStore);
        return http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterAfter(rateLimitFilter, CorsFilter.class)
                .addFilterAfter(adminSessionAuthenticationFilter, RateLimitFilter.class)
                .addFilterAfter(adminApiKeyFilter, AdminSessionAuthenticationFilter.class)
                .addFilterBefore(deviceAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(anonymousTrialFilter, DeviceAuthenticationFilter.class)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new ApiAuthenticationEntryPoint())
                        .accessDeniedHandler(new ApiAccessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/scans/*")
                        .hasAuthority(DeviceAuthenticationFilter.LICENSED_DEVICE_AUTHORITY)
                        .requestMatchers(HttpMethod.POST, "/api/v1/scans/*/explanation")
                        .hasAuthority(DeviceAuthenticationFilter.LICENSED_DEVICE_AUTHORITY)
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin-auth/session")
                        .hasAuthority(AdminSessionAuthenticationFilter.ADMIN_AUTHORITY)
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin-auth/logout")
                        .hasAuthority(AdminSessionAuthenticationFilter.ADMIN_AUTHORITY)
                        .anyRequest().permitAll())
                .build();
    }

    /**
     * BCrypt for administrator passwords ({@code admin.application.AdminAuthService}). No other
     * password model exists in this codebase: end users have none, and {@code ADMIN_API_KEY} is a
     * constant-time-compared shared secret, not a hashed password.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Restricts cross-origin access to the configured origins.
     *
     * <p>Origins come from {@code linksentry.cors.allowed-origins} and are matched
     * exactly. {@code allowCredentials} stays off because the API sends no cookies;
     * enabling it would also make a wildcard origin illegal, which is a useful
     * reminder that wildcards have no place here.
     *
     * <p>Declared as the concrete {@code UrlBasedCorsConfigurationSource} rather than
     * the {@code CorsConfigurationSource} interface: Spring MVC's
     * {@code HandlerMappingIntrospector} also implements that interface, so injecting
     * by the interface type would be ambiguous. The bean <em>name</em> still matters —
     * Spring Security's {@code cors()} DSL looks for {@code corsConfigurationSource}.
     */
    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(properties.allowedMethods());
        // X-Admin-Api-Key is deliberately absent: it is an operator-only fallback,
        // never a browser credential, even though browser admin sessions may call
        // the same admin routes with Authorization: Bearer.
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept", "Authorization"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
