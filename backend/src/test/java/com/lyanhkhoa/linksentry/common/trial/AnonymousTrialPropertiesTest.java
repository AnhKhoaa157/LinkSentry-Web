package com.lyanhkhoa.linksentry.common.trial;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link AnonymousTrialProperties} through the real Spring Boot relaxed-binding and Bean
 * Validation pipeline (no application context beyond the properties bean, no Docker) so an invalid
 * configuration fails application startup instead of silently running a broken or unbounded trial
 * quota. {@code store.*} was removed under ADR 0010 — the quota is PostgreSQL-persisted, not an
 * in-memory map — so there is no idle-expiration/window cross-field rule left to test.
 */
class AnonymousTrialPropertiesTest {

    private static final String PREFIX = "linksentry.anonymous-trial.";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

    @Test
    @DisplayName("the documented defaults (3 scans, 24h window) bind successfully")
    void defaultConfigurationBindsSuccessfully() {
        runWith(true, 3, Duration.ofHours(24)).run(context -> {
            assertThat(context).hasNotFailed();
            AnonymousTrialProperties properties = context.getBean(AnonymousTrialProperties.class);
            assertThat(properties.enabled()).isTrue();
            assertThat(properties.maxScans()).isEqualTo(3);
            assertThat(properties.window()).isEqualTo(Duration.ofHours(24));
        });
    }

    @Test
    @DisplayName("a zero rolling window fails application startup")
    void zeroWindowFailsBinding() {
        runWith(true, 3, Duration.ZERO).run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("a negative rolling window fails application startup")
    void negativeWindowFailsBinding() {
        runWith(true, 3, Duration.ofHours(-1)).run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("a zero maxScans fails application startup")
    void zeroMaxScansFailsBinding() {
        runWith(true, 0, Duration.ofHours(24)).run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("disabled=false still binds — the filter passes every request through unchecked")
    void disabledBindsSuccessfully() {
        runWith(false, 3, Duration.ofHours(24)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AnonymousTrialProperties.class).enabled()).isFalse();
        });
    }

    private ApplicationContextRunner runWith(boolean enabled, int maxScans, Duration window) {
        return contextRunner.withPropertyValues(
                PREFIX + "enabled=" + enabled, PREFIX + "max-scans=" + maxScans, PREFIX + "window=" + window);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AnonymousTrialProperties.class)
    static class TestConfiguration {}
}
