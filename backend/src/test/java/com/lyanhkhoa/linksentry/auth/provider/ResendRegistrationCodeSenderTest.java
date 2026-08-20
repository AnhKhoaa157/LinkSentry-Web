package com.lyanhkhoa.linksentry.auth.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lyanhkhoa.linksentry.auth.application.MailDeliveryException;
import com.lyanhkhoa.linksentry.common.config.ResendMailProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Covers every documented failure category for the Resend HTTPS adapter —
 * missing configuration, a network/timeout failure, and a non-2xx Resend
 * response — collapsing to the same fixed {@link MailDeliveryException}
 * without leaking the API key, the recipient, the code, or the provider's
 * own response text into the exception, a log message, or the outbound
 * request beyond what is intended. Also covers the constructed request
 * shape on a successful send.
 */
class ResendRegistrationCodeSenderTest {

    private static final String EMAIL = "person@example.com";
    private static final String CODE = "123456";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String API_KEY = "re_secret_test_key";
    private static final String FROM = "LinkSentry <noreply@example.com>";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("no configured API key is a safe unavailable error, HTTP client never invoked")
    void missingApiKeyIsSafeErrorAndNeverSends() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        ResendRegistrationCodeSender sender =
                new ResendRegistrationCodeSender(new ResendMailProperties("", FROM), httpClient);

        assertThatThrownBy(() -> sender.send(EMAIL, CODE, TTL))
                .isInstanceOf(MailDeliveryException.class)
                .hasMessageNotContaining(EMAIL)
                .hasMessageNotContaining(CODE)
                .hasMessageNotContaining(API_KEY);
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @DisplayName("a blank configured from-address is a safe unavailable error, HTTP client never invoked")
    void missingFromIsSafeErrorAndNeverSends() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        ResendRegistrationCodeSender sender =
                new ResendRegistrationCodeSender(new ResendMailProperties(API_KEY, "  "), httpClient);

        assertThatThrownBy(() -> sender.send(EMAIL, CODE, TTL)).isInstanceOf(MailDeliveryException.class);
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @DisplayName("a connection/timeout failure is a safe unavailable error, never the exception's own message")
    void networkFailureIsSafeError() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connect timed out talking to api.resend.com"));
        ResendRegistrationCodeSender sender =
                new ResendRegistrationCodeSender(new ResendMailProperties(API_KEY, FROM), httpClient);

        assertThatThrownBy(() -> sender.send(EMAIL, CODE, TTL))
                .isInstanceOf(MailDeliveryException.class)
                .hasMessageNotContaining("api.resend.com")
                .hasMessageNotContaining(EMAIL)
                .hasMessageNotContaining(CODE);
    }

    @Test
    @DisplayName("a non-2xx Resend response is a safe unavailable error, never a retry")
    void nonSuccessResponseIsSafeErrorAndDoesNotRetry() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(422);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        ResendRegistrationCodeSender sender =
                new ResendRegistrationCodeSender(new ResendMailProperties(API_KEY, FROM), httpClient);

        assertThatThrownBy(() -> sender.send(EMAIL, CODE, TTL)).isInstanceOf(MailDeliveryException.class);
        verify(httpClient, org.mockito.Mockito.times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("a successful send builds one fixed HTTPS POST with only the recipient, code, and TTL")
    void successfulSendBuildsExpectedRequest() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        ResendRegistrationCodeSender sender =
                new ResendRegistrationCodeSender(new ResendMailProperties(API_KEY, FROM), httpClient);

        sender.send(EMAIL, CODE, TTL);

        HttpRequest request = requestCaptor.getValue();
        assertThat(request.uri()).isEqualTo(URI.create("https://api.resend.com/emails"));
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers().firstValue("Authorization")).contains("Bearer " + API_KEY);
        assertThat(request.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(request.timeout()).isPresent();
        assertThat(request.timeout().orElseThrow()).isLessThanOrEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("the request body contains only the recipient, code, TTL, subject, and configured from-address")
    void buildBodyContainsOnlyExpectedFields() throws Exception {
        String body = ResendRegistrationCodeSender.buildBody(FROM, EMAIL, CODE, TTL);

        ObjectNode parsed = (ObjectNode) OBJECT_MAPPER.readTree(body);
        assertThat(parsed.get("from").asText()).isEqualTo(FROM);
        assertThat(parsed.get("to").get(0).asText()).isEqualTo(EMAIL);
        assertThat(parsed.get("subject").asText()).isEqualTo("Verify your LinkSentry account");
        assertThat(parsed.get("text").asText()).contains(CODE).contains("10 minutes");
        assertThat(parsed.size()).isEqualTo(4);
        assertThat(body).doesNotContain(API_KEY);
    }

    @Test
    @DisplayName("the built HTTP client follows no redirects and applies an explicit connect timeout")
    void builtHttpClientNeverFollowsRedirectsAndHasExplicitConnectTimeout() {
        ResendRegistrationCodeSender sender =
                new ResendRegistrationCodeSender(new ResendMailProperties(API_KEY, FROM));

        HttpClient client = extractHttpClient(sender);
        assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        assertThat(client.connectTimeout()).isPresent();
    }

    private static HttpClient extractHttpClient(ResendRegistrationCodeSender sender) {
        try {
            var field = ResendRegistrationCodeSender.class.getDeclaredField("httpClient");
            field.setAccessible(true);
            return (HttpClient) field.get(sender);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
