package com.lyanhkhoa.linksentry.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Optional AI-explanation feature, bound from {@code linksentry.ai-explanation.*}.
 *
 * <p>Disabled by default. {@code anthropic.apiKey} and {@code anthropic.model} may
 * be blank while {@code enabled} is {@code false} — nothing reads them in that
 * state — but the moment {@code enabled} is {@code true} both become mandatory, so
 * a deployment that turns the feature on without finishing configuration fails
 * fast at startup instead of returning a vague failure on the first request. See
 * {@code explanation.provider.AnthropicExplanationProvider} and
 * {@code docs/adr/0005-anthropic-scan-explanation-integration.md}.
 *
 * @param enabled   turns the feature on or off entirely; endpoint and UI both
 *                  respect this
 * @param anthropic provider credentials and model selection; never client-supplied
 */
@Validated
@ConfigurationProperties(prefix = "linksentry.ai-explanation")
public record AiExplanationProperties(boolean enabled, Anthropic anthropic) {

    public AiExplanationProperties {
        if (enabled) {
            if (anthropic == null || isBlank(anthropic.apiKey())) {
                throw new IllegalArgumentException(
                        "linksentry.ai-explanation.anthropic.api-key is required when "
                                + "linksentry.ai-explanation.enabled is true");
            }
            if (isBlank(anthropic.model())) {
                throw new IllegalArgumentException(
                        "linksentry.ai-explanation.anthropic.model is required when "
                                + "linksentry.ai-explanation.enabled is true");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * @param apiKey secret credential; never logged, returned, or persisted
     * @param model  server-selected model id; never client-controlled
     */
    public record Anthropic(String apiKey, String model) {}
}
