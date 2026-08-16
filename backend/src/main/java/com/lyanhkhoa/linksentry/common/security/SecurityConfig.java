package com.lyanhkhoa.linksentry.common.security;

import com.lyanhkhoa.linksentry.common.config.CorsProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless security baseline for the scaffold.
 *
 * <p>The API is currently anonymous by design: URL analysis needs no identity,
 * and adding authentication before there is anything to protect would be
 * ceremony. What this configuration does provide is the correct <em>shape</em> for
 * an API that will never use cookies:
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
 * </ul>
 *
 * <p>Rate limiting and authentication are prerequisites for a public deployment
 * and are deliberately out of scope here.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http, UrlBasedCorsConfigurationSource corsConfigurationSource) throws Exception {
        return http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
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
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
