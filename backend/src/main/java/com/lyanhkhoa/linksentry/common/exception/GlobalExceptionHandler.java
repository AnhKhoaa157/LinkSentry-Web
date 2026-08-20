package com.lyanhkhoa.linksentry.common.exception;

import com.lyanhkhoa.linksentry.admin.application.InvalidAdminCredentialsException;
import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.common.api.ErrorResponse;
import com.lyanhkhoa.linksentry.explanation.application.ExplanationUnavailableException;
import com.lyanhkhoa.linksentry.history.application.ScanNotFoundException;
import com.lyanhkhoa.linksentry.license.application.DeviceAlreadyAssignedException;
import com.lyanhkhoa.linksentry.license.application.DeviceAssignmentNotFoundException;
import com.lyanhkhoa.linksentry.license.application.DeviceLimitExceededException;
import com.lyanhkhoa.linksentry.license.application.DeviceNotFoundException;
import com.lyanhkhoa.linksentry.license.application.InvalidDeviceCredentialException;
import com.lyanhkhoa.linksentry.license.application.LicenseNotFoundException;
import com.lyanhkhoa.linksentry.license.application.LicenseRevokedException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
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

    /** An admin login attempt with an unknown username or wrong password. */
    @ExceptionHandler(InvalidAdminCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAdminCredentials(InvalidAdminCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_CREDENTIALS", "Username or password is incorrect.", newTraceId()));
    }

    /**
     * A genuinely authenticated principal with the wrong domain authority for the route — a licensed
     * device on an admin-only route, or an administrator on a device-only route — reached a controller
     * directly. {@code SecurityConfig}'s {@code hasAuthority(...)} check normally rejects this earlier,
     * via {@code common.security.ApiAccessDeniedHandler}; this handler is what a controller's own
     * defensive type check (see {@code scan.api.ScanController}, {@code explanation.api.ExplanationController},
     * and {@code admin.api.AdminAuthController}) falls back to, and it also covers a {@code
     * @WebMvcTest} slice with the security filter chain disabled. Same fixed {@code 403} envelope
     * either way — never which authority was expected or which principal type was found.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        String traceId = newTraceId();
        log.info("Access denied [traceId={}]", traceId);

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        "FORBIDDEN", "You do not have permission to access this resource.", traceId));
    }

    /** A device status check without a recognisable {@code Authorization: Device ...} header. */
    @ExceptionHandler(InvalidDeviceCredentialException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDeviceCredential(InvalidDeviceCredentialException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(
                        "INVALID_DEVICE_CREDENTIAL", "The device credential is missing or invalid.", newTraceId()));
    }

    /** An admin request naming an unknown license ID. */
    @ExceptionHandler(LicenseNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleLicenseNotFound(LicenseNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("LICENSE_NOT_FOUND", "The requested license could not be found.", newTraceId()));
    }

    /** An admin request naming an unknown device or activation code. */
    @ExceptionHandler(DeviceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDeviceNotFound(DeviceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("DEVICE_NOT_FOUND", "The requested device could not be found.", newTraceId()));
    }

    /** Granting a device that already holds an active assignment to some license. */
    @ExceptionHandler(DeviceAlreadyAssignedException.class)
    public ResponseEntity<ErrorResponse> handleDeviceAlreadyAssigned(DeviceAlreadyAssignedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("DEVICE_ALREADY_ASSIGNED", exception.getMessage(), newTraceId()));
    }

    /** Granting a device would exceed a license's configured device cap. */
    @ExceptionHandler(DeviceLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleDeviceLimitExceeded(DeviceLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("DEVICE_LIMIT_EXCEEDED", exception.getMessage(), newTraceId()));
    }

    /** Granting a device against a license that is already revoked. */
    @ExceptionHandler(LicenseRevokedException.class)
    public ResponseEntity<ErrorResponse> handleLicenseRevoked(LicenseRevokedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("LICENSE_REVOKED", exception.getMessage(), newTraceId()));
    }

    /** Revoking a device that has no currently active license assignment. */
    @ExceptionHandler(DeviceAssignmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDeviceAssignmentNotFound(DeviceAssignmentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("DEVICE_ASSIGNMENT_NOT_FOUND", exception.getMessage(), newTraceId()));
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
