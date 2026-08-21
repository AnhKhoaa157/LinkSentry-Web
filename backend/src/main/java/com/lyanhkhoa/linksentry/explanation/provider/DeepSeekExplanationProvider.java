package com.lyanhkhoa.linksentry.explanation.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lyanhkhoa.linksentry.common.config.AiExplanationProperties;
import com.lyanhkhoa.linksentry.explanation.domain.ExplanationProvider;
import com.lyanhkhoa.linksentry.explanation.domain.ExplanationProviderException;
import com.lyanhkhoa.linksentry.explanation.domain.ScanSummary;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The one isolated outbound adapter permitted to call DeepSeek. See
 * {@code docs/adr/0005-deepseek-scan-explanation-integration.md}.
 *
 * <p>Every call is a single synchronous, non-streaming HTTPS POST to DeepSeek's
 * OpenAI-compatible chat-completions endpoint, made with the JDK's own
 * {@link HttpClient} (no HTTP-client or DeepSeek SDK dependency was added for
 * this one call site) and given exactly one attempt with an explicit 20-second
 * request timeout — {@code HttpClient.send} performs no retry loop, so nothing
 * outside {@link AiExplanationProperties} controls the endpoint, key, or model
 * this class talks to. The request body built by {@link #buildUserPrompt(ScanSummary)}
 * reads only {@link ScanSummary}'s fields, which structurally exclude every raw
 * or identifying value already excluded from that type.
 */
@Component
public class DeepSeekExplanationProvider implements ExplanationProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekExplanationProvider.class);

    private static final URI ENDPOINT = URI.create("https://api.deepseek.com/chat/completions");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final long MAX_TOKENS = 300L;

    // Explicit failure mode, not a silent truncation: a response longer than this
    // is treated the same as any other unusable provider response.
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
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final Duration requestTimeout;

    @Autowired
    public DeepSeekExplanationProvider(AiExplanationProperties properties) {
        this(properties, properties.enabled() ? buildHttpClient() : null, new ObjectMapper(), ENDPOINT, REQUEST_TIMEOUT);
    }

    /** Test-only seam: injects a caller-supplied client instead of building a real one. */
    DeepSeekExplanationProvider(AiExplanationProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        this(properties, httpClient, objectMapper, ENDPOINT, REQUEST_TIMEOUT);
    }

    /** Test-only seam: additionally overrides the endpoint and request timeout, so tests can point
     *  this class at a local mock server and exercise a real timeout deterministically without
     *  waiting 20 real seconds. */
    DeepSeekExplanationProvider(
            AiExplanationProperties properties,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            URI endpoint,
            Duration requestTimeout) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public String explain(ScanSummary summary) {
        if (httpClient == null) {
            // Defensive only: ExplanationService checks properties.enabled() before
            // ever reaching this call, so a real deployment never hits this branch.
            throw new ExplanationProviderException("AI explanation provider is disabled");
        }

        String requestBody = buildRequestBody(summary);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.deepseek().apiKey())
                .POST(BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, BodyHandlers.ofString());
        } catch (IOException exception) {
            // Never exception.getMessage(): it may quote host/request detail.
            log.warn("DeepSeek call failed [type={}]", exception.getClass().getSimpleName());
            throw new ExplanationProviderException("DeepSeek call failed");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExplanationProviderException("DeepSeek call was interrupted");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // Never the response body: DeepSeek's error payload could quote request detail.
            log.warn("DeepSeek call returned a non-2xx status [status={}]", response.statusCode());
            throw new ExplanationProviderException("DeepSeek call returned a non-2xx status");
        }

        return extractText(response.body());
    }

    private String extractText(String responseBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new ExplanationProviderException("DeepSeek response was not valid JSON");
        }
        return extractTextFromResponse(root);
    }

    /** Pure and independently testable: reads the first choice's message content and bounds its length. */
    static String extractTextFromResponse(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new ExplanationProviderException("DeepSeek response contained no choices");
        }
        String text = choices.get(0).path("message").path("content").asText("").strip();
        if (text.isEmpty()) {
            throw new ExplanationProviderException("DeepSeek response contained no usable text");
        }
        if (text.length() > MAX_EXPLANATION_LENGTH) {
            throw new ExplanationProviderException("DeepSeek response exceeded the maximum allowed length");
        }
        return text;
    }

    private String buildRequestBody(ScanSummary summary) {
        Map<String, Object> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", SYSTEM_PROMPT);

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", buildUserPrompt(summary));

        Map<String, Object> thinking = new LinkedHashMap<>();
        thinking.put("type", "disabled");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.deepseek().model());
        body.put("messages", List.of(systemMessage, userMessage));
        body.put("stream", false);
        body.put("max_tokens", MAX_TOKENS);
        // Fixed deployment policy, never configurable: short scan explanations stay
        // fast and inexpensive under deepseek-v4-flash's default (thinking-enabled)
        // behavior, so thinking mode is explicitly turned off on every call.
        body.put("thinking", thinking);

        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            // Every value above is server-built from ScanSummary/configuration and is
            // always serializable; this branch defends against a future field, not a
            // realistic runtime failure.
            throw new ExplanationProviderException("Failed to build the DeepSeek request body");
        }
    }

    /**
     * Builds the entire outbound request body's user content. Reads only
     * {@code summary}'s fields — {@link ScanSummary} has no field for a raw URL, a
     * redacted display value, a hostname, a port, a query, a fragment, a
     * credential, a remote address, a bearer token, an email, a scan ID, a trace
     * ID, or finding evidence, so none of those can appear here no matter what
     * this method does.
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

    private static HttpClient buildHttpClient() {
        return HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    }
}
