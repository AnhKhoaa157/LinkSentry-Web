package com.lyanhkhoa.linksentry.common.exception;

import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.auth.application.EmailAlreadyRegisteredException;
import com.lyanhkhoa.linksentry.auth.application.InvalidCredentialsException;
import com.lyanhkhoa.linksentry.common.api.ErrorResponse;
import com.lyanhkhoa.linksentry.explanation.application.ExplanationUnavailableException;
import com.lyanhkhoa.linksentry.history.application.ScanNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates exceptions into the single documented {@link ErrorResponse} envelope.
 *
 * <p>Two rules govern everything here:
 *
 * <ol>
 *   <li><strong>Nothing internal escapes.</strong> Unexpected failures return a
 *       fixed generic message. The exception itself is logged with a
 *       {@code traceId} that the client also receives, so an operator can find the
 *       stack trace without the client ever seeing it.
 *   <li><strong>Nothing user-submitted is logged.</strong> A rejected value may be
 *       a URL carrying a session token in its query string. Log the failure and
 *       the field <em>name</em>, never the value.
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String GENERIC_MESSAGE = "The request could not be completed. Please try again later.";

    /** Bean Validation failures on a request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            // Only the field name and the constraint message — never the value.
            fieldErrors.putIfAbsent(
                    fieldError.getField(),
                    fieldError.getDefaultMessage() == null ? "Invalid value." : fieldError.getDefaultMessage());
        }

        String traceId = newTraceId();
        log.info("Validation failed [traceId={}] for fields {}", traceId, fieldErrors.keySet());

        return ResponseEntity.badRequest()
                .body(ErrorResponse.ofFieldErrors(
                        "VALIDATION_ERROR", "The request contains invalid values.", fieldErrors, traceId));
    }

    /** Submitted value is not an analysable {@code http}/{@code https} URL. */
    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUrl(InvalidUrlException exception) {
        String traceId = newTraceId();
        // Keep the log fixed even if a future validation path changes its exception text.
        log.info("Invalid URL rejected [traceId={}]", traceId);

        return ResponseEntity.badRequest()
                .body(ErrorResponse.ofFieldErrors(
                        "INVALID_URL",
                        "The submitted value is not a supported HTTP or HTTPS URL.",
                        Map.of("url", "Enter a valid HTTP or HTTPS URL."),
                        traceId));
    }

    /** Registration conflict without echoing the submitted address. */
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "EMAIL_ALREADY_REGISTERED",
                        "An account already exists for this email address.",
                        newTraceId()));
    }

    /** Login failures intentionally do not distinguish an unknown email from a bad password. */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(
                        "INVALID_CREDENTIALS", "Email or password is incorrect.", newTraceId()));
    }

    /** Missing, malformed, and expired opaque scan IDs share one safe response. */
    @ExceptionHandler(ScanNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleScanNotFound(ScanNotFoundException exception) {
        String traceId = newTraceId();
        log.info("Scan not found [traceId={}]", traceId);

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("SCAN_NOT_FOUND", "The requested scan could not be found.", traceId));
    }

    /**
     * The AI explanation feature is disabled, unconfigured, or the provider could
     * not produce a result. One fixed, vendor-free message and status covers every
     * cause — disabled, missing configuration, timeout, provider failure, and a
     * malformed provider response are all indistinguishable to the client.
     */
    @ExceptionHandler(ExplanationUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleExplanationUnavailable(ExplanationUnavailableException exception) {
        String traceId = newTraceId();
        log.info("AI explanation unavailable [traceId={}]", traceId);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(
                        "AI_EXPLANATION_UNAVAILABLE", "AI explanation is not available right now.", traceId));
    }

    /** Unparseable or absent request body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        String traceId = newTraceId();
        // The exception message can quote the offending payload, so it is not logged.
        log.info("Unreadable request body [traceId={}]", traceId);

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("MALFORMED_REQUEST", "The request body is not valid JSON.", traceId));
    }

    /** Unknown route. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", "The requested resource does not exist.", newTraceId()));
    }

    /** Known route, wrong HTTP method. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of(
                        "METHOD_NOT_ALLOWED", "This method is not supported for the requested resource.",
                        newTraceId()));
    }

    /** Anything unanticipated. The client learns only that it failed. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        String traceId = newTraceId();
        log.error("Unhandled exception [traceId={}]", traceId, exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", GENERIC_MESSAGE, traceId));
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString();
    }
}
