package com.lyanhkhoa.linksentry.explanation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonMissing;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.services.blocking.MessageService;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import com.lyanhkhoa.linksentry.common.config.AiExplanationProperties;
import com.lyanhkhoa.linksentry.explanation.domain.ExplanationProviderException;
import com.lyanhkhoa.linksentry.explanation.domain.ScanSummary;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the one class allowed to call Anthropic: a successful response, every
 * documented failure mode (timeout/IO, a provider HTTP error, and a malformed
 * response with no usable text), and that neither the built prompt nor the
 * system prompt can leak vendor detail or claim a link is safe.
 *
 * <p>{@link AnthropicClient} and {@link MessageService} are mocked interfaces —
 * safe to mock. {@link Message}, {@link ContentBlock}, and {@link TextBlock} are
 * real SDK response objects built through their own builders instead: they are
 * ordinary (non-mockable) classes generated from the OpenAPI spec, and every
 * field {@link AnthropicExplanationProvider} does not read is forced to
 * {@link JsonMissing#of()}, the SDK's own documented escape hatch for a required
 * builder field the test does not care about. No real network call is made, and
 * {@code AnthropicOkHttpClient} is never constructed — the package-private test
 * constructor injects the mock client directly.
 */
class AnthropicExplanationProviderTest {

    private final AiExplanationProperties properties =
            new AiExplanationProperties(true, new AiExplanationProperties.Anthropic("test-key", "test-model"));

    @Test
    @DisplayName("a successful call returns the joined text of every text content block")
    void successfulCallReturnsJoinedText() {
        MessageService messageService = mock(MessageService.class);
        Message message = realMessageWithContent(List.of(
                contentBlockOfText("This link shows several risk signals."),
                contentBlockOfText("Consider verifying it before continuing.")));
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        AnthropicExplanationProvider provider = providerWith(messageService);

        String result = provider.explain(sampleSummary());

        assertThat(result)
                .isEqualTo("This link shows several risk signals. Consider verifying it before continuing.");
    }

    @Test
    @DisplayName("a response with no content blocks at all is a safe malformed-response error")
    void noContentBlocksIsMalformedResponse() {
        MessageService messageService = mock(MessageService.class);
        Message message = realMessageWithContent(List.of());
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        AnthropicExplanationProvider provider = providerWith(messageService);

        assertThatThrownBy(() -> provider.explain(sampleSummary())).isInstanceOf(ExplanationProviderException.class);
    }

    @Test
    @DisplayName("a blank-only text response is a safe malformed-response error")
    void blankTextResponseIsMalformedResponse() {
        MessageService messageService = mock(MessageService.class);
        Message message = realMessageWithContent(List.of(contentBlockOfText("   ")));
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        AnthropicExplanationProvider provider = providerWith(messageService);

        assertThatThrownBy(() -> provider.explain(sampleSummary())).isInstanceOf(ExplanationProviderException.class);
    }

    @Test
    @DisplayName("a network timeout is a safe unavailable error, never the vendor's own message")
    void timeoutIsSafeProviderError() {
        MessageService messageService = mock(MessageService.class);
        when(messageService.create(any(MessageCreateParams.class)))
                .thenThrow(new AnthropicIoException("connect timed out after 20000ms to api.anthropic.com"));
        AnthropicExplanationProvider provider = providerWith(messageService);

        assertThatThrownBy(() -> provider.explain(sampleSummary()))
                .isInstanceOf(ExplanationProviderException.class)
                .hasMessageNotContaining("api.anthropic.com")
                .hasMessageNotContaining("20000");
    }

    @Test
    @DisplayName("a provider HTTP error is a safe unavailable error, never the vendor's response body")
    void providerHttpErrorIsSafeProviderError() {
        MessageService messageService = mock(MessageService.class);
        BadRequestException badRequest = BadRequestException.builder()
                .headers(Headers.builder().build())
                .body(JsonValue.from(Map.of("error", Map.of("message", "a secret internal detail"))))
                .build();
        when(messageService.create(any(MessageCreateParams.class))).thenThrow(badRequest);
        AnthropicExplanationProvider provider = providerWith(messageService);

        assertThatThrownBy(() -> provider.explain(sampleSummary()))
                .isInstanceOf(ExplanationProviderException.class)
                .hasMessageNotContaining("a secret internal detail");
    }

    @Test
    @DisplayName("a disabled provider (no client built) is a safe unavailable error, defensively")
    void disabledProviderIsSafeErrorAndNeverBuildsAClient() {
        AiExplanationProperties disabled = new AiExplanationProperties(false, null);
        AnthropicExplanationProvider provider = new AnthropicExplanationProvider(disabled, null);

        assertThatThrownBy(() -> provider.explain(sampleSummary())).isInstanceOf(ExplanationProviderException.class);
    }

    @Test
    @DisplayName("the built prompt contains the required summary fields")
    void promptContainsRequiredSummaryFields() {
        String prompt = AnthropicExplanationProvider.buildUserPrompt(sampleSummary());

        assertThat(prompt)
                .contains("score: 75/100")
                .contains("CRITICAL")
                .contains("0.1.0")
                .contains("BRAND_DOMAIN_MISMATCH")
                .contains("HIGH")
                .contains("30")
                .contains("Hostname names a brand it is not registered to");
    }

    @Test
    @DisplayName("the built prompt never contains a forbidden field, because ScanSummary has none to read")
    void promptNeverContainsForbiddenFields() {
        String prompt = AnthropicExplanationProvider.buildUserPrompt(sampleSummary());

        assertThat(prompt)
                .doesNotContain("http://")
                .doesNotContain("https://")
                .doesNotContainIgnoringCase("evidence")
                .doesNotContainIgnoringCase("scanId");
    }

    @Test
    @DisplayName("the system prompt forbids claiming safety and forbids markup or links in the reply")
    void systemPromptForbidsSafetyClaimsAndMarkup() {
        // Collapse whitespace (the constant wraps across lines) so a substring check
        // is not sensitive to exactly where a line happens to break.
        String lower = AnthropicExplanationProvider.SYSTEM_PROMPT.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        // The instruction must name the forbidden claim to forbid it ("never state or
        // imply that the link is safe") — so this asserts the *instruction*, not the
        // absence of the phrase "is safe", which the instruction necessarily contains.
        assertThat(lower)
                .contains("never state or imply that the link is safe")
                .contains("no signal was detected, not that nothing is wrong")
                .contains("never invent a")
                .contains("plain sentences only");
    }

    private AnthropicExplanationProvider providerWith(MessageService messageService) {
        AnthropicClient client = mock(AnthropicClient.class);
        when(client.messages()).thenReturn(messageService);
        return new AnthropicExplanationProvider(properties, client);
    }

    private static ScanSummary sampleSummary() {
        ScanSummary.FindingSummary finding = new ScanSummary.FindingSummary(
                "BRAND_DOMAIN_MISMATCH",
                Severity.HIGH,
                30,
                "Hostname names a brand it is not registered to",
                "Generic rule explanation text.");
        return new ScanSummary(75, RiskLevel.CRITICAL, "0.1.0", List.of(finding));
    }

    private static ContentBlock contentBlockOfText(String text) {
        return ContentBlock.ofText(
                TextBlock.builder().text(text).citations(List.of()).build());
    }

    /**
     * A real, minimal {@link Message}. Every required field this provider does not
     * read is forced to {@link JsonMissing#of()} — the SDK's own documented way to
     * satisfy a builder's required field without asserting a value for it — since
     * {@link Message} is not an interface and its accessors are not mockable.
     */
    private static Message realMessageWithContent(List<ContentBlock> content) {
        return Message.builder()
                .id(JsonMissing.of())
                .model(JsonMissing.of())
                .stopDetails(JsonMissing.of())
                .stopReason(JsonMissing.of())
                .stopSequence(JsonMissing.of())
                .usage(JsonMissing.of())
                .content(content)
                .build();
    }
}
