package com.lyanhkhoa.linksentry.auth.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lyanhkhoa.linksentry.auth.application.MailDeliveryException;
import com.lyanhkhoa.linksentry.auth.application.RegistrationCodeSender;
import com.lyanhkhoa.linksentry.common.config.ResendMailProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sends only the normalized registration email address, a newly generated
 * six-digit code, and fixed explanatory text through the Resend HTTPS API
 * ({@code POST https://api.resend.com/emails}, port 443). It never receives a
 * submitted URL, a password, a bearer token, scan data, or a raw request body.
 *
 * <p>Deliberately no SMTP: Render Free blocks ports 25, 465, and 587, so this
 * adapter talks only HTTPS. The outbound {@link HttpClient} is built once with an
 * explicit connect timeout, follows no redirects, and is given exactly one
 * attempt per call — the JDK does not retry a POST automatically. See
 * {@code docs/SECURITY_BOUNDARY.md} §9.
 */
@Component
public class ResendRegistrationCodeSender implements RegistrationCodeSender {

    private static final Logger log = LoggerFactory.getLogger(ResendRegistrationCodeSender.class);

    private static final URI RESEND_ENDPOINT = URI.create("https://api.resend.com/emails");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final String SUBJECT = "Verify your LinkSentry account";

    // Self-contained, like common.security.ApiAuthenticationEntryPoint's mapper: this
    // adapter's JSON shape is fixed and trivial, so it does not depend on an
    // application-wide Jackson bean.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ResendMailProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public ResendRegistrationCodeSender(ResendMailProperties properties) {
        this(properties, buildClient());
    }

    /** Test-only seam: injects a caller-supplied client instead of building a real one. */
    ResendRegistrationCodeSender(ResendMailProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public void send(String email, String code, Duration ttl) {
        String apiKey = properties.apiKey();
        String from = properties.from();
        if (apiKey == null || apiKey.isBlank() || from == null || from.isBlank()) {
            log.warn("Registration email not sent [category=NOT_CONFIGURED]");
            throw new MailDeliveryException();
        }

        String body;
        try {
            body = buildBody(from, email, code, ttl);
        } catch (JsonProcessingException exception) {
            log.warn("Registration email not sent [category=REQUEST_BUILD]");
            throw new MailDeliveryException();
        }

        HttpRequest request = HttpRequest.newBuilder(RESEND_ENDPOINT)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<Void> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException exception) {
            // Never exception.getMessage(): it can quote host/response detail. Only the
            // exception's own class name is safe to log — same discipline as the SMTP
            // adapter this class replaces and explanation.AnthropicExplanationProvider.
            log.warn(
                    "Registration email delivery failed [category=CONNECTIVITY, type={}]",
                    exception.getClass().getSimpleName());
            throw new MailDeliveryException();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Registration email delivery failed [category=INTERRUPTED]");
            throw new MailDeliveryException();
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // Never the response body: Resend's error payload can echo the request,
            // including the recipient address.
            log.warn(
                    "Registration email delivery failed [category=PROVIDER_ERROR, status={}]",
                    response.statusCode());
            throw new MailDeliveryException();
        }
    }

    /** Pure and independently testable: the entire outbound request body. */
    static String buildBody(String from, String email, String code, Duration ttl) throws JsonProcessingException {
        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("from", from);
        body.putArray("to").add(email);
        body.put("subject", SUBJECT);
        body.put(
                "text",
                "Your LinkSentry verification code is " + code + ". It expires in " + ttl.toMinutes()
                        + " minutes. If you did not request this, you can ignore this email.");
        return OBJECT_MAPPER.writeValueAsString(body);
    }

    private static HttpClient buildClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
