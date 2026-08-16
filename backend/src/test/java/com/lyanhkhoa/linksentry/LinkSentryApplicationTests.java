package com.lyanhkhoa.linksentry;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.common.config.CorsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Proves the whole application context starts.
 *
 * <p>Cheap to write and disproportionately useful: it catches a missing bean, a
 * broken {@code @ConfigurationProperties} binding, or a validation failure at the
 * point where the message is still readable.
 */
@SpringBootTest
@ActiveProfiles("test")
class LinkSentryApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CorsProperties corsProperties;

    @Test
    @DisplayName("application context loads")
    void contextLoads() {
        assertThat(context).isNotNull();
        assertThat(context.getBean("corsConfigurationSource", UrlBasedCorsConfigurationSource.class))
                .isNotNull();
    }

    @Test
    @DisplayName("CORS origins are bound from configuration and contain no wildcard")
    void corsPropertiesAreBound() {
        assertThat(corsProperties.allowedOrigins()).containsExactly("http://localhost:5173");
        assertThat(corsProperties.allowedMethods()).contains("GET", "POST");
        assertThat(corsProperties.allowedOrigins()).doesNotContain("*");
    }
}
