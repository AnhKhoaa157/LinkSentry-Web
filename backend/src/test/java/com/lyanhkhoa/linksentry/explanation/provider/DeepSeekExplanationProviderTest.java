package com.lyanhkhoa.linksentry.explanation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import com.lyanhkhoa.linksentry.common.config.AiExplanationProperties;
import com.lyanhkhoa.linksentry.explanation.domain.ExplanationProviderException;
import com.lyanhkhoa.linksentry.explanation.domain.ScanSummary;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the one class allowed to call DeepSeek. Never calls the real DeepSeek
 * API or uses a real key: a local {@link HttpServer} (JDK built-in, no new test
 * dependency) stands in for DeepSeek so the endpoint path, the {@code
 * Authorization} header, and the exact request payload can be asserted against
 * the real {@link DeepSeekExplanationProvider#explain(ScanSummary)}, and so every
 * documented failure mode (timeout, non-2xx, malformed body, empty body,
 * oversized body) can be produced deterministically. The package-private test
 * constructor injects an {@link HttpClient}, the mock server's own {@link URI},
 * and (for the timeout test only) a short request timeout, instead of DeepSeek's
 * real fixed endpoint and 20-second production timeout.
 */
class DeepSeekExplanationProviderTest {

    private static final Duration DEFAULT_TEST_TIMEOUT = Duration.ofSeconds(5);

    private HttpServer server;
    private URI endpoint;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        endpoint = URI.create("http://localhost:" + server.getAddress().getPort() + "/chat/completions");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("a successful call sends the documented endpoint, Bearer header, and payload, and returns the text")
    void successfulCallSendsExpectedRequestAndReturnsText() throws IOException {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/chat/completions", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"This link shows several risk signals.\"}}]}");
        });
        server.start();
        DeepSeekExplanationProvider provider = providerWith("test-key", "test-model", DEFAULT_TEST_TIMEOUT);

        String result = provider.explain(sampleSummary());

        assertThat(result).isEqualTo("This link shows several risk signals.");
        assertThat(capturedPath.get()).isEqualTo("/chat/completions");
        assertThat(capturedAuth.get()).isEqualTo("Bearer test-key");

        JsonNode body = objectMapper.readTree(capturedBody.get());
        assertThat(body.get("model").asText()).isEqualTo("test-model");
        assertThat(body.get("stream").asBoolean()).isFalse();
        assertThat(body.has("max_tokens")).isTrue();
        assertThat(body.has("thinking")).isTrue();
        assertThat(body.get("thinking").get("type").asText()).isEqualTo("disabled");
        assertThat(body.get("messages")).hasSize(2);
        assertThat(body.get("messages").get(0).get("role").asText()).isEqualTo("system");
        assertThat(body.get("messages").get(1).get("role").asText()).isEqualTo("user");
        String userContent = body.get("messages").get(1).get("content").asText();
        assertThat(userContent)
                .contains("score: 75/100")
                .contains("CRITICAL")
                .contains("BRAND_DOMAIN_MISMATCH")
                .doesNotContain("http://")
                .doesNotContain("https://")
                .doesNotContainIgnoringCase("evidence")
                .doesNotContainIgnoringCase("scanId");
        // Only the allowed keys are present in the request payload — no forbidden field slipped in.
        List<String> fieldNames = new ArrayList<>();
        body.fieldNames().forEachRemaining(fieldNames::add);
        assertThat(fieldNames).containsExactlyInAnyOrder("model", "messages", "stream", "max_tokens", "thinking");
    }

    @Test
    @DisplayName("a non-2xx response is a safe unavailable error, never the response body")
    void nonTwoXxResponseIsSafeProviderError() throws IOException {
        server.createContext(
                "/chat/completions",
                exchange -> respond(exchange, 401, "{\"error\":{\"message\":\"a secret internal detail\"}}"));
        server.start();
        DeepSeekExplanationProvider provider = providerWith("test-key", "test-model", DEFAULT_TEST_TIMEOUT);

        assertThatThrownBy(() -> provider.explain(sampleSummary()))
                .isInstanceOf(ExplanationProviderException.class)
                .hasMessageNotContaining("a secret internal detail");
    }

    @Test
    @DisplayName("a malformed (non-JSON) response body is a safe unavailable error")
    void malformedResponseBodyIsSafeProviderError() throws IOException {
        server.createContext("/chat/completions", exchange -> respond(exchange, 200, "not json"));
        server.start();
        DeepSeekExplanationProvider provider = providerWith("test-key", "test-model", DEFAULT_TEST_TIMEOUT);

        assertThatThrownBy(() -> provider.explain(sampleSummary())).isInstanceOf(ExplanationProviderException.class);
    }

    @Test
    @DisplayName("a response with no choices at all is a safe unavailable error")
    void emptyChoicesIsSafeProviderError() throws IOException {
        server.createContext("/chat/completions", exchange -> respond(exchange, 200, "{\"choices\":[]}"));
        server.start();
        DeepSeekExplanationProvider provider = providerWith("test-key", "test-model", DEFAULT_TEST_TIMEOUT);

        assertThatThrownBy(() -> provider.explain(sampleSummary())).isInstanceOf(ExplanationProviderException.class);
    }

    @Test
    @DisplayName("a blank-only message content is a safe unavailable error")
    void blankContentIsSafeProviderError() throws IOException {
        server.createContext(
                "/chat/completions",
                exchange -> respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"   \"}}]}"));
        server.start();
        DeepSeekExplanationProvider provider = providerWith("test-key", "test-model", DEFAULT_TEST_TIMEOUT);

        assertThatThrownBy(() -> provider.explain(sampleSummary())).isInstanceOf(ExplanationProviderException.class);
    }

    @Test
    @DisplayName("a response exceeding the 1,000-character cap is a safe unavailable error, not a silent truncation")
    void oversizedResponseIsSafeProviderError() throws IOException {
        String longText = "a".repeat(1_001);
        server.createContext(
                "/chat/completions",
                exchange -> respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"" + longText + "\"}}]}"));
        server.start();
        DeepSeekExplanationProvider provider = providerWith("test-key", "test-model", DEFAULT_TEST_TIMEOUT);

        assertThatThrownBy(() -> provider.explain(sampleSummary())).isInstanceOf(ExplanationProviderException.class);
    }

    @Test
    @DisplayName("a network timeout is a safe unavailable error, never the vendor's own message")
    void timeoutIsSafeProviderError() throws IOException {
        server.createContext("/chat/completions", exchange -> {
            try {
                // Sleep well past the short request timeout used only in this test.
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"too late\"}}]}");
        });
        server.start();
        DeepSeekExplanationProvider provider = providerWith("test-key", "test-model", Duration.ofMillis(200));

        assertThatThrownBy(() -> provider.explain(sampleSummary()))
                .isInstanceOf(ExplanationProviderException.class)
                .hasMessageNotContaining("localhost")
                .hasMessageNotContaining("too late");
    }

    @Test
    @DisplayName("a disabled provider (no client built) is a safe unavailable error, defensively")
    void disabledProviderIsSafeErrorAndNeverBuildsAClient() {
        AiExplanationProperties disabled = new AiExplanationProperties(false, null);
        DeepSeekExplanationProvider provider = new DeepSeekExplanationProvider(disabled, null, objectMapper);

        assertThatThrownBy(() -> provider.explain(sampleSummary())).isInstanceOf(ExplanationProviderException.class);
    }

    @Test
    @DisplayName("the built prompt contains the required summary fields")
    void promptContainsRequiredSummaryFields() {
        String prompt = DeepSeekExplanationProvider.buildUserPrompt(sampleSummary());

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
        String prompt = DeepSeekExplanationProvider.buildUserPrompt(sampleSummary());

        assertThat(prompt)
                .doesNotContain("http://")
                .doesNotContain("https://")
                .doesNotContainIgnoringCase("evidence")
                .doesNotContainIgnoringCase("scanId");
    }

    @Test
    @DisplayName("the system prompt forbids claiming safety and forbids markup or links in the reply")
    void systemPromptForbidsSafetyClaimsAndMarkup() {
        String lower =
                DeepSeekExplanationProvider.SYSTEM_PROMPT.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertThat(lower)
                .contains("never state or imply that the link is safe")
                .contains("no signal was detected, not that nothing is wrong")
                .contains("never invent a")
                .contains("plain sentences only");
    }

    private DeepSeekExplanationProvider providerWith(String apiKey, String model, Duration requestTimeout) {
        AiExplanationProperties properties =
                new AiExplanationProperties(true, new AiExplanationProperties.DeepSeek(apiKey, model));
        HttpClient client = HttpClient.newBuilder().connectTimeout(DEFAULT_TEST_TIMEOUT).build();
        return new DeepSeekExplanationProvider(properties, client, objectMapper, endpoint, requestTimeout);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
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
}
