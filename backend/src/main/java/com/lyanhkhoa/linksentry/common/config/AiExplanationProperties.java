package com.lyanhkhoa.linksentry.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Optional AI-explanation feature, bound from {@code linksentry.ai-explanation.*}.
 *
 * <p>Disabled by default. {@code deepseek.apiKey} and {@code deepseek.model} may
 * be blank while {@code enabled} is {@code false} — nothing reads them in that
 * state — but the moment {@code enabled} is {@code true} both become mandatory, so
 * a deployment that turns the feature on without finishing configuration fails
 * fast at startup instead of returning a vague failure on the first request.
 * {@code model} has no hard-coded default: a deployment must name a real DeepSeek
 * model id explicitly. See
 * {@code explanation.provider.DeepSeekExplanationProvider} and
 * {@code docs/adr/0005-deepseek-scan-explanation-integration.md}.
 *
 * @param enabled  turns the feature on or off entirely; endpoint and UI both
 *                 respect this
 * @param deepseek provider credentials and model selection; never client-supplied
 */
@Validated
@ConfigurationProperties(prefix = "linksentry.ai-explanation")
public record AiExplanationProperties(boolean enabled, DeepSeek deepseek) {

    public AiExplanationProperties {
        if (enabled) {
            if (deepseek == null || isBlank(deepseek.apiKey())) {
                throw new IllegalArgumentException(
                        "linksentry.ai-explanation.deepseek.api-key is required when "
                                + "linksentry.ai-explanation.enabled is true");
            }
            if (isBlank(deepseek.model())) {
                throw new IllegalArgumentException(
                        "linksentry.ai-explanation.deepseek.model is required when "
                                + "linksentry.ai-explanation.enabled is true");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * @param apiKey secret credential; never logged, returned, or persisted
     * @param model  server-selected model id; never client-controlled, no
     *               hard-coded default
     */
    public record DeepSeek(String apiKey, String model) {}
}
