package com.lyanhkhoa.linksentry.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application's single source of "now".
 *
 * <p>Injecting {@link Clock} rather than calling {@code Instant.now()} directly
 * lets any time-stamping code be tested with a fixed instant instead of asserting
 * against a moving target.
 */
@Configuration
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
