package com.lyanhkhoa.linksentry.common.trial;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link AnonymousTrialProperties} through the real Spring Boot relaxed-binding
 * and Bean Validation pipeline (no application context beyond the properties bean, no
 * Docker) so every invalid-duration combination fails application startup instead of
 * silently running a broken or unbounded trial quota. See
 * {@code docs/SECURITY_BOUNDARY.md} §6 for why the rolling window and idle-expiration
 * boundary must stay strict.
 */
class AnonymousTrialPropertiesTest {

    private static final String PREFIX = "linksentry.anonymous-trial.";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

    @Test
    @DisplayName("equal window and idle-expiration durations bind successfully (inclusive boundary)")
    void equalDurationsBindSuccessfully() {
        runWith(Duration.ofHours(2), Duration.ofHours(2))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AnonymousTrialProperties properties = context.getBean(AnonymousTrialProperties.class);
                    assertThat(properties.window()).isEqualTo(Duration.ofHours(2));
                    assertThat(properties.store().idleExpiration()).isEqualTo(Duration.ofHours(2));
                });
    }

    @Test
    @DisplayName("an idle-expiration longer than window binds successfully")
    void longerIdleExpirationBindsSuccessfully() {
        runWith(Duration.ofHours(2), Duration.ofHours(3))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("the documented defaults (3 scans, 24h window, 24h idle-expiration) bind successfully")
    void defaultConfigurationBindsSuccessfully() {
        runWith(Duration.ofHours(24), Duration.ofHours(24))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AnonymousTrialProperties properties = context.getBean(AnonymousTrialProperties.class);
                    assertThat(properties.maxScans()).isEqualTo(3);
                    assertThat(properties.window()).isEqualTo(Duration.ofHours(24));
                    assertThat(properties.store().idleExpiration()).isEqualTo(Duration.ofHours(24));
                });
    }

    @Test
    @DisplayName("a zero rolling window fails application startup")
    void zeroWindowFailsBinding() {
        runWith(Duration.ZERO, Duration.ofHours(24)).run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("a negative rolling window fails application startup")
    void negativeWindowFailsBinding() {
        runWith(Duration.ofHours(-1), Duration.ofHours(24)).run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("a zero idle-expiration fails application startup")
    void zeroIdleExpirationFailsBinding() {
        runWith(Duration.ofHours(24), Duration.ZERO).run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("a negative idle-expiration fails application startup")
    void negativeIdleExpirationFailsBinding() {
        runWith(Duration.ofHours(24), Duration.ofHours(-1)).run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("an idle-expiration shorter than the rolling window fails application startup with a clear cause")
    void idleExpirationShorterThanWindowFailsBinding() {
        runWith(Duration.ofHours(2), Duration.ofHours(1)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining(
                            "linksentry.anonymous-trial.store.idle-expiration must be greater than or equal to "
                                    + "linksentry.anonymous-trial.window");
        });
    }

    private ApplicationContextRunner runWith(Duration window, Duration idleExpiration) {
        return contextRunner.withPropertyValues(
                PREFIX + "enabled=true",
                PREFIX + "max-scans=3",
                PREFIX + "window=" + window,
                PREFIX + "store.max-entries=10000",
                PREFIX + "store.idle-expiration=" + idleExpiration);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AnonymousTrialProperties.class)
    static class TestConfiguration {}
}
