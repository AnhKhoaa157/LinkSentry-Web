package com.lyanhkhoa.linksentry.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link AiExplanationProperties} through the real Spring Boot
 * relaxed-binding pipeline (no application context beyond the properties bean, no
 * Docker, no real Anthropic credential) so a deployment that turns the feature on
 * without finishing configuration fails application startup with a clear error
 * instead of failing on the first request.
 */
class AiExplanationPropertiesTest {

    private static final String PREFIX = "linksentry.ai-explanation.";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

    @Test
    @DisplayName("disabled with no key or model configured still binds successfully")
    void disabledWithNoConfigurationBindsSuccessfully() {
        contextRunner.withPropertyValues(PREFIX + "enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            AiExplanationProperties properties = context.getBean(AiExplanationProperties.class);
            assertThat(properties.enabled()).isFalse();
        });
    }

    @Test
    @DisplayName("disabled with a blank key and model still binds successfully")
    void disabledWithBlankValuesBindsSuccessfully() {
        contextRunner
                .withPropertyValues(
                        PREFIX + "enabled=false", PREFIX + "anthropic.api-key=", PREFIX + "anthropic.model=")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("enabled with both a key and a model binds successfully")
    void enabledWithCompleteConfigurationBindsSuccessfully() {
        contextRunner
                .withPropertyValues(
                        PREFIX + "enabled=true",
                        PREFIX + "anthropic.api-key=sk-ant-test-key",
                        PREFIX + "anthropic.model=claude-haiku-4-5")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AiExplanationProperties properties = context.getBean(AiExplanationProperties.class);
                    assertThat(properties.anthropic().apiKey()).isEqualTo("sk-ant-test-key");
                    assertThat(properties.anthropic().model()).isEqualTo("claude-haiku-4-5");
                });
    }

    @Test
    @DisplayName("enabled with no anthropic block at all fails application startup")
    void enabledWithNoAnthropicBlockFailsBinding() {
        contextRunner.withPropertyValues(PREFIX + "enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("linksentry.ai-explanation.anthropic.api-key is required");
        });
    }

    @Test
    @DisplayName("enabled with a blank api key fails application startup")
    void enabledWithBlankApiKeyFailsBinding() {
        contextRunner
                .withPropertyValues(
                        PREFIX + "enabled=true", PREFIX + "anthropic.api-key=", PREFIX + "anthropic.model=claude-haiku-4-5")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("linksentry.ai-explanation.anthropic.api-key is required");
                });
    }

    @Test
    @DisplayName("enabled with a key but a blank model fails application startup")
    void enabledWithBlankModelFailsBinding() {
        contextRunner
                .withPropertyValues(
                        PREFIX + "enabled=true", PREFIX + "anthropic.api-key=sk-ant-test-key", PREFIX + "anthropic.model=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("linksentry.ai-explanation.anthropic.model is required");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiExplanationProperties.class)
    static class TestConfiguration {}
}
