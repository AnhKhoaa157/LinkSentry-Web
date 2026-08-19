package com.lyanhkhoa.linksentry.explanation.provider;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.lyanhkhoa.linksentry.common.config.AiExplanationProperties;
import com.lyanhkhoa.linksentry.explanation.domain.ExplanationProvider;
import com.lyanhkhoa.linksentry.explanation.domain.ExplanationProviderException;
import com.lyanhkhoa.linksentry.explanation.domain.ScanSummary;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The one isolated outbound adapter permitted to call Anthropic. See
 * {@code docs/adr/0005-anthropic-scan-explanation-integration.md}.
 *
 * <p>Every call is synchronous, non-streaming, uses no tool, and is given exactly
 * one attempt ({@code maxRetries(0)}) with an explicit short timeout — never the
 * SDK's environment-driven {@code fromEnv()} construction, so nothing outside
 * {@link AiExplanationProperties} controls the endpoint, key, or model this
 * class talks to. The request body built by {@link #buildUserPrompt(ScanSummary)}
 * reads only {@link ScanSummary}'s fields, which structurally exclude every raw
 * or identifying value already excluded from that type.
 */
@Component
public class AnthropicExplanationProvider implements ExplanationProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicExplanationProvider.class);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final long MAX_TOKENS = 300L;

    // A generous ceiling given maxTokens already bounds the response; a defensive
    // second bound in case a future model/response shape returns more text per token.
    private static final int MAX_EXPLANATION_LENGTH = 1_000;

    static final String SYSTEM_PROMPT =
            """
            You are a security assistant inside LinkSentry, a URL risk-analysis tool
            that inspects link text only and never visits, fetches, or resolves the
            destination. You will be given a machine-generated risk summary for one
            already-completed scan: a numeric score, a risk-level band, an engine
            version, and an ordered list of findings, each with a rule id, severity,
            points, a short title, and a short generic explanation.

            Write two or three plain-language sentences for a non-expert reader
            explaining why this summary suggests the level of caution it does.

            Rules:
            - Use risk-oriented language only. Never state or imply that the link is
              safe, trustworthy, or verified — the absence of a strong finding means
              no signal was detected, not that nothing is wrong.
            - Base your answer only on the summary you are given. Never invent a
              finding, a hostname, a brand, or any detail not present in it.
            - Do not include a URL, a link, markup, or code in your reply — plain
              sentences only.
            """;

    private final AiExplanationProperties properties;
    private final AnthropicClient client;

    @Autowired
    public AnthropicExplanationProvider(AiExplanationProperties properties) {
        this(properties, properties.enabled() ? buildClient(properties) : null);
    }

    /** Test-only seam: injects a caller-supplied client instead of building a real one. */
    AnthropicExplanationProvider(AiExplanationProperties properties, AnthropicClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public String explain(ScanSummary summary) {
        if (client == null) {
            // Defensive only: ExplanationService checks properties.enabled() before
            // ever reaching this call, so a real deployment never hits this branch.
            throw new ExplanationProviderException("AI explanation provider is disabled");
        }

        MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.anthropic().model())
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT)
                .addUserMessage(buildUserPrompt(summary))
                .build();

        Message message;
        try {
            message = client.messages().create(params);
        } catch (AnthropicException exception) {
            // Never exception.getMessage(): the SDK may quote request or response
            // detail in it. Only the exception's class name is safe to log.
            log.warn("Anthropic call failed [type={}]", exception.getClass().getSimpleName());
            throw new ExplanationProviderException("Anthropic call failed");
        }

        return extractText(message);
    }

    private static String extractText(Message message) {
        return extractTextFromBlocks(message.content());
    }

    /** Pure and independently testable: joins every text block, strips, and bounds the length. */
    static String extractTextFromBlocks(List<ContentBlock> blocks) {
        String text = blocks.stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining(" "))
                .strip();
        if (text.isEmpty()) {
            throw new ExplanationProviderException("Anthropic response contained no usable text");
        }
        return text.length() > MAX_EXPLANATION_LENGTH ? text.substring(0, MAX_EXPLANATION_LENGTH) : text;
    }

    /**
     * Builds the entire outbound request body. Reads only {@code summary}'s
     * fields — {@link ScanSummary} has no field for a raw URL, a redacted display
     * value, a hostname, a port, a query, a fragment, a credential, a remote
     * address, a bearer token, an email, a scan ID, a trace ID, or finding
     * evidence, so none of those can appear here no matter what this method does.
     */
    static String buildUserPrompt(ScanSummary summary) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("score: ").append(summary.score()).append("/100\n");
        prompt.append("riskLevel: ").append(summary.riskLevel()).append('\n');
        prompt.append("engineVersion: ").append(summary.engineVersion()).append('\n');
        if (summary.findings().isEmpty()) {
            prompt.append("findings: none\n");
        } else {
            prompt.append("findings:\n");
            for (ScanSummary.FindingSummary finding : summary.findings()) {
                prompt.append("- ruleId=")
                        .append(finding.ruleId())
                        .append(", severity=")
                        .append(finding.severity())
                        .append(", points=")
                        .append(finding.points())
                        .append(", title=\"")
                        .append(finding.title())
                        .append('"')
                        .append(", explanation=\"")
                        .append(finding.explanation())
                        .append('"')
                        .append('\n');
            }
        }
        return prompt.toString();
    }

    private static AnthropicClient buildClient(AiExplanationProperties properties) {
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.anthropic().apiKey())
                .maxRetries(0)
                .timeout(REQUEST_TIMEOUT)
                .build();
    }
}
