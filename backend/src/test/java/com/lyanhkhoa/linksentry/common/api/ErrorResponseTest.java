package com.lyanhkhoa.linksentry.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.ObjectMapper;

/**
 * Locks down the error envelope's wire shape.
 *
 * <p>Field names are part of the published contract, and the absent-when-empty
 * behaviour of {@code fieldErrors} is what keeps a non-validation error from
 * carrying a misleading empty object.
 */
@JsonTest
class ErrorResponseTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("serialises the documented field names")
    void serialisesDocumentedFields() throws Exception {
        ErrorResponse response = ErrorResponse.of("INTERNAL_ERROR", "Something went wrong.", "trace-1");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json)
                .contains("\"code\":\"INTERNAL_ERROR\"")
                .contains("\"message\":\"Something went wrong.\"")
                .contains("\"traceId\":\"trace-1\"")
                .contains("\"timestamp\"");
    }

    @Test
    @DisplayName("omits fieldErrors when there are none")
    void omitsAbsentFieldErrors() throws Exception {
        ErrorResponse response = ErrorResponse.of("NOT_FOUND", "Nope.", "trace-2");

        assertThat(objectMapper.writeValueAsString(response)).doesNotContain("fieldErrors");
    }

    @Test
    @DisplayName("includes per-field messages when validation failed")
    void includesFieldErrors() throws Exception {
        ErrorResponse response = ErrorResponse.ofFieldErrors(
                "VALIDATION_ERROR",
                "The request contains invalid values.",
                Map.of("url", "Enter a valid HTTP or HTTPS URL."),
                "trace-3");

        assertThat(objectMapper.writeValueAsString(response))
                .contains("\"fieldErrors\":{\"url\":\"Enter a valid HTTP or HTTPS URL.\"}");
    }

    @Test
    @DisplayName("field errors are defensively copied")
    void fieldErrorsAreImmutable() {
        Map<String, String> mutable = new java.util.HashMap<>(Map.of("url", "Invalid."));
        ErrorResponse response = ErrorResponse.ofFieldErrors("VALIDATION_ERROR", "Invalid.", mutable, "trace-4");

        mutable.put("url", "tampered");

        assertThat(response.fieldErrors()).containsExactly(Map.entry("url", "Invalid."));
    }
}
