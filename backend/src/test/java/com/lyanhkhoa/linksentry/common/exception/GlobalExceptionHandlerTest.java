package com.lyanhkhoa.linksentry.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lyanhkhoa.linksentry.health.HealthController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifies the error envelope through a real request cycle.
 *
 * <p>Uses a test-only fixture controller rather than a production endpoint, so the
 * exception handling can be locked down independently. {@code @WebMvcTest} without
 * a narrowed {@code controllers} filter still picks up every production
 * {@code @RestController} on the classpath (including {@code ScanController}), so
 * its dependency is mocked here purely to satisfy the context — this slice never
 * calls it. Security filters are disabled so the assertions are about error
 * mapping alone.
 */
@WebMvcTest(controllers = HealthController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.FixtureController.class})
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("validation failures return 400 with per-field messages")
    void validationFailureReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/test-fixture/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.value").exists())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("unreadable JSON returns 400 MALFORMED_REQUEST without echoing the body")
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(post("/test-fixture/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("The request body is not valid JSON."));
    }

    @Test
    @DisplayName("unexpected failures return a generic 500 that leaks nothing")
    void unexpectedFailureIsGeneric() throws Exception {
        mockMvc.perform(post("/test-fixture/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                // The client must learn nothing about the cause.
                .andExpect(jsonPath("$.message").value("The request could not be completed. Please try again later."))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    @DisplayName("wrong HTTP method returns 405")
    void wrongMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("an access-denied failure returns a generic 403 that names neither the required authority nor the principal")
    void accessDeniedReturnsGenericForbidden() throws Exception {
        mockMvc.perform(post("/test-fixture/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource."))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    /** Test-only endpoints that trigger each error path. Never registered in production. */
    @RestController
    static class FixtureController {

        record FixtureRequest(@NotBlank String value) {}

        @PostMapping("/test-fixture/validated")
        String validated(@Valid @RequestBody FixtureRequest request) {
            return request.value();
        }

        @PostMapping("/test-fixture/boom")
        String boom() {
            throw new IllegalStateException("internal detail that must not reach the client");
        }

        @PostMapping("/test-fixture/forbidden")
        String forbidden() {
            throw new AccessDeniedException("internal detail that must not reach the client");
        }
    }
}
