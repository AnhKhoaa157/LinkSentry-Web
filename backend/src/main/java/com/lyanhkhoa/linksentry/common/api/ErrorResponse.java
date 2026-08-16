package com.lyanhkhoa.linksentry.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * The single error envelope returned by every failing endpoint.
 *
 * <p>Deliberately does <strong>not</strong> carry a stack trace, an exception
 * class name, or a framework message: those disclose implementation detail to
 * anyone probing the API. {@link #traceId()} is the only link between what the
 * client sees and what the server logged.
 *
 * @param code        stable machine-readable error code, e.g. {@code INVALID_URL}
 * @param message     human-readable summary, safe to display to an end user
 * @param fieldErrors per-field validation messages; omitted from JSON when absent
 * @param traceId     correlation id matching a server-side log entry
 * @param timestamp   when the error was produced, in UTC
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code, String message, Map<String, String> fieldErrors, String traceId, Instant timestamp) {

    /** Creates an error without field-level detail. */
    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(code, message, null, traceId, Instant.now());
    }

    /** Creates a validation error carrying per-field messages. */
    public static ErrorResponse ofFieldErrors(
            String code, String message, Map<String, String> fieldErrors, String traceId) {
        return new ErrorResponse(code, message, Map.copyOf(fieldErrors), traceId, Instant.now());
    }
}
