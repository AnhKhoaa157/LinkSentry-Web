package com.lyanhkhoa.linksentry.common.security;

import com.lyanhkhoa.linksentry.auth.application.AuthService;
import com.lyanhkhoa.linksentry.common.config.CorsProperties;
import com.lyanhkhoa.linksentry.common.ratelimit.RateLimitBucketStore;
import com.lyanhkhoa.linksentry.common.ratelimit.RateLimitFilter;
import com.lyanhkhoa.linksentry.common.ratelimit.RateLimitProperties;
import com.lyanhkhoa.linksentry.common.ratelimit.RouteClassifier;
import com.lyanhkhoa.linksentry.common.trial.AnonymousTrialFilter;
import com.lyanhkhoa.linksentry.common.trial.AnonymousTrialProperties;
import com.lyanhkhoa.linksentry.common.trial.AnonymousTrialStore;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Stateless security baseline for public scans and private bearer history.
 *
 * <p>URL analysis remains public for one-off scans, while account/session routes
 * and retained history require the opaque bearer identity installed by the
 * custom filter. The API never uses cookies:
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
 *   <li><strong>Anonymous trial quota.</strong> {@link AnonymousTrialFilter} sits
 *       immediately after {@link BearerTokenAuthenticationFilter}, so it can tell an
 *       authenticated caller apart from an anonymous one and never gates the former.
 *       It is independent of rate limiting — both apply to every anonymous scan. See
 *       {@code common.trial}.
 * </ul>
 *
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
            AuthService authService,
            AnonymousTrialProperties anonymousTrialProperties,
            AnonymousTrialStore anonymousTrialStore)
            throws Exception {
        RateLimitFilter rateLimitFilter =
                new RateLimitFilter(rateLimitProperties, rateLimitRouteClassifier, rateLimitBucketStore);
        BearerTokenAuthenticationFilter bearerTokenFilter = new BearerTokenAuthenticationFilter(authService);
        AnonymousTrialFilter anonymousTrialFilter =
                new AnonymousTrialFilter(anonymousTrialProperties, anonymousTrialStore);
        return http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterAfter(rateLimitFilter, CorsFilter.class)
                .addFilterBefore(bearerTokenFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(anonymousTrialFilter, BearerTokenAuthenticationFilter.class)
                .exceptionHandling(exception -> exception.authenticationEntryPoint(new ApiAuthenticationEntryPoint()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/session", "/api/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/scans/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/scans/*/explanation").authenticated()
                        .anyRequest().permitAll())
                .build();
    }

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
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept", "Authorization"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
