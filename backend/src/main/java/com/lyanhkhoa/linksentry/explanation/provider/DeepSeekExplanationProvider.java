package com.lyanhkhoa.linksentry.explanation.provider;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lyanhkhoa.linksentry.common.config.AiExplanationProperties;
import com.lyanhkhoa.linksentry.explanation.domain.AiAdvisory;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *
 * <p>The model is asked for DeepSeek's documented JSON mode
 * ({@code response_format: {"type": "json_object"}}) and must return a strict
 * JSON object with exactly two keys: {@code summary} (a single concise,
 * risk-oriented advisory sentence) and {@code recommendedActions} (an array of
 * one or two short actions). {@link #parseAdvisory(String)} validates that
 * shape strictly — a required nonblank summary, 1-2 nonblank actions, and
 * bounded lengths on both — and treats any deviation the same as every other
 * provider failure: a safe {@link ExplanationProviderException}, never a
 * best-effort partial result. The model never sets risk level, findings,
 * severity, or points; those are assembled deterministically by
 * {@code explanation.application.ExplanationService} from the retained scan.
 */
@Component
public class DeepSeekExplanationProvider implements ExplanationProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekExplanationProvider.class);

    private static final URI ENDPOINT = URI.create("https://api.deepseek.com/chat/completions");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final long MAX_TOKENS = 300L;

    // Explicit failure modes, not silent truncation: a response outside these
    // bounds is treated the same as any other unusable provider response.
    private static final int MAX_RAW_RESPONSE_LENGTH = 2_000;
    private static final int MAX_SUMMARY_LENGTH = 300;
    private static final int MAX_ACTION_LENGTH = 200;
    private static final int MIN_ACTIONS = 1;
    private static final int MAX_ACTIONS = 2;

    // The complete, exact set of top-level keys DeepSeek's structured output may
    // carry — nothing more, nothing fewer, and never a repeated key. See
    // parseAdvisory's duplicate/exact-key pass.
    private static final Set<String> ALLOWED_TOP_LEVEL_KEYS = Set.of("summary", "recommendedActions");

    static final String SYSTEM_PROMPT =
            """
            You are a security assistant inside LinkSentry, a URL risk-analysis tool
            that inspects link text only and never visits, fetches, or resolves the
            destination. You will be given a machine-generated risk summary for one
            already-completed scan: a numeric score, a risk-level band, an engine
            version, and an ordered list of findings, each with a rule id, severity,
            points, a short title, and a short generic explanation.

            The backend has already decided the risk level, the score, and the
            findings; you do not set, restate as a decision, or override any of them.
            Your only job is to produce two things:
            1. "summary": one concise, plain-language, risk-oriented sentence (no more
               than 300 characters) explaining why this summary suggests the level of
               caution it does.
            2. "recommendedActions": an array of exactly one or two short, concrete,
               plain-language actions the reader should take, given the risk level.

            Respond with a single strict JSON object and nothing else — no prose
            before or after it, no code fence — matching exactly this shape:
            {"summary": "...", "recommendedActions": ["...", "..."]}

            Rules:
            - Use risk-oriented language only. Never state or imply that the link is
              safe, verified, trustworthy, phishing, or malicious, and never claim
              certainty — the absence of a strong finding means no signal was
              detected, not that nothing is wrong.
            - Base your answer only on the summary you are given. Never invent a
              finding, a hostname, a domain, a brand, or any detail not present in it.
            - Do not include a URL, a link, markup, HTML, or code in your reply —
              plain sentences only.
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
    public AiAdvisory explain(ScanSummary summary) {
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

        return extractAdvisory(response.body());
    }

    private AiAdvisory extractAdvisory(String responseBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new ExplanationProviderException("DeepSeek response was not valid JSON");
        }
        String content = extractContent(root);
        return parseAdvisory(content, objectMapper);
    }

    /** Pure and independently testable: reads the first choice's message content and bounds its length. */
    static String extractContent(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new ExplanationProviderException("DeepSeek response contained no choices");
        }
        String text = choices.get(0).path("message").path("content").asText("").strip();
        if (text.isEmpty()) {
            throw new ExplanationProviderException("DeepSeek response contained no usable text");
        }
        if (text.length() > MAX_RAW_RESPONSE_LENGTH) {
            throw new ExplanationProviderException("DeepSeek response exceeded the maximum allowed length");
        }
        return text;
    }

    /**
     * Strictly validates the model's structured JSON output: exactly the two
     * allowed top-level keys ({@link #ALLOWED_TOP_LEVEL_KEYS}) — no missing key,
     * no extra/unrecognized key, and no repeated key, checked in
     * {@link #validateExactTopLevelKeys} before any value is trusted — a required
     * nonblank {@code summary} within {@link #MAX_SUMMARY_LENGTH}, and a
     * {@code recommendedActions} array of {@link #MIN_ACTIONS}-{@link #MAX_ACTIONS}
     * nonblank strings, each within {@link #MAX_ACTION_LENGTH}. Any deviation —
     * missing key, extra key, duplicate key, wrong type, blank value, too
     * many/too few actions, oversized text — is the same safe
     * {@link ExplanationProviderException} every other provider failure
     * produces; there is no best-effort partial parse.
     */
    static AiAdvisory parseAdvisory(String content, ObjectMapper objectMapper) {
        validateExactTopLevelKeys(content, objectMapper);

        JsonNode node;
        try {
            node = objectMapper.readTree(content);
        } catch (JsonProcessingException exception) {
            throw new ExplanationProviderException("DeepSeek structured output was not valid JSON");
        }
        if (!node.isObject()) {
            throw new ExplanationProviderException("DeepSeek structured output was not a JSON object");
        }

        JsonNode summaryNode = node.path("summary");
        if (!summaryNode.isTextual()) {
            throw new ExplanationProviderException("DeepSeek structured output had no textual summary");
        }
        String summary = summaryNode.asText().strip();
        if (summary.isEmpty() || summary.length() > MAX_SUMMARY_LENGTH) {
            throw new ExplanationProviderException("DeepSeek summary was blank or too long");
        }

        JsonNode actionsNode = node.path("recommendedActions");
        if (!actionsNode.isArray()) {
            throw new ExplanationProviderException("DeepSeek structured output had no recommendedActions array");
        }
        List<String> actions = new ArrayList<>();
        for (JsonNode actionNode : actionsNode) {
            if (!actionNode.isTextual()) {
                throw new ExplanationProviderException("DeepSeek recommendedActions entry was not text");
            }
            String action = actionNode.asText().strip();
            if (action.isEmpty() || action.length() > MAX_ACTION_LENGTH) {
                throw new ExplanationProviderException("DeepSeek recommendedActions entry was blank or too long");
            }
            actions.add(action);
        }
        if (actions.size() < MIN_ACTIONS || actions.size() > MAX_ACTIONS) {
            throw new ExplanationProviderException("DeepSeek recommendedActions had an unexpected count");
        }

        return new AiAdvisory(summary, actions);
    }

    /**
     * A streaming, token-level pass over the raw JSON text that runs before
     * {@link ObjectMapper#readTree} ever builds a tree — deliberately, because
     * {@link JsonNode}'s object model silently collapses a repeated key to its
     * last occurrence, so a tree-based check alone could never tell "the model
     * sent exactly {@code summary} and {@code recommendedActions}" apart from
     * "the model sent {@code summary} twice and {@code recommendedActions} was
     * missing." This method rejects both, along with any key outside
     * {@link #ALLOWED_TOP_LEVEL_KEYS} and any output that is not a single JSON
     * object at the top level.
     */
    private static void validateExactTopLevelKeys(String content, ObjectMapper objectMapper) {
        Set<String> seenKeys = new HashSet<>();
        try (JsonParser parser = objectMapper.getFactory().createParser(content)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new ExplanationProviderException("DeepSeek structured output was not a JSON object");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String fieldName = parser.currentName();
                if (!ALLOWED_TOP_LEVEL_KEYS.contains(fieldName) || !seenKeys.add(fieldName)) {
                    throw new ExplanationProviderException(
                            "DeepSeek structured output had an unexpected or duplicate key");
                }
                parser.nextToken();
                parser.skipChildren();
            }
            // A trailing token after the closing brace (a second top-level value,
            // trailing garbage) is rejected rather than silently ignored.
            if (parser.nextToken() != null) {
                throw new ExplanationProviderException("DeepSeek structured output had trailing content");
            }
        } catch (IOException exception) {
            throw new ExplanationProviderException("DeepSeek structured output was not valid JSON");
        }
        if (!seenKeys.equals(ALLOWED_TOP_LEVEL_KEYS)) {
            throw new ExplanationProviderException("DeepSeek structured output was missing a required key");
        }
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

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_object");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.deepseek().model());
        body.put("messages", List.of(systemMessage, userMessage));
        body.put("stream", false);
        body.put("max_tokens", MAX_TOKENS);
        // Fixed deployment policy, never configurable: short scan explanations stay
        // fast and inexpensive under deepseek-v4-flash's default (thinking-enabled)
        // behavior, so thinking mode is explicitly turned off on every call.
        body.put("thinking", thinking);
        // DeepSeek's documented JSON mode: constrains the model to return one
        // strict JSON object, which parseAdvisory then validates independently.
        body.put("response_format", responseFormat);

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
