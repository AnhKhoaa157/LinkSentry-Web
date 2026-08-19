package com.lyanhkhoa.linksentry.explanation.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lyanhkhoa.linksentry.auth.security.AuthenticatedUser;
import com.lyanhkhoa.linksentry.common.exception.GlobalExceptionHandler;
import com.lyanhkhoa.linksentry.explanation.application.ExplanationService;
import com.lyanhkhoa.linksentry.explanation.application.ExplanationUnavailableException;
import com.lyanhkhoa.linksentry.history.application.ScanNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Covers the wiring of {@code POST /api/v1/scans/{scanId}/explanation}: delegation
 * to {@link ExplanationService} with the authenticated caller's own user ID, and
 * exception mapping to the documented safe responses.
 *
 * <p>The real security filter chain is disabled in this slice
 * ({@code addFilters = false}, the same as {@code ScanControllerTest}), so the
 * authenticated-caller scenarios set the request's principal directly via
 * {@code MockHttpServletRequestBuilder.principal(...)} — the servlet-level
 * mechanism Spring MVC's built-in {@code Authentication}/{@code Principal}
 * argument resolution reads regardless of the (disabled) filter chain — rather
 * than asserting the route's own {@code .authenticated()} requirement, which is
 * a {@code common.security.SecurityConfig} concern, not this controller's.
 */
@WebMvcTest(ExplanationController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class ExplanationControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SCAN_ID = UUID.fromString("2ce16fb9-d52d-4310-8d45-a4e48f31889e");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExplanationService explanationService;

    @Test
    @DisplayName("an authenticated owner receives the generated explanation, addressed by their own user ID")
    void authenticatedOwnerReceivesExplanation() throws Exception {
        given(explanationService.explain(anyString(), any(UUID.class)))
                .willReturn("This link shows several risk signals worth a second look.");

        mockMvc.perform(post("/api/v1/scans/{scanId}/explanation", SCAN_ID).principal(authenticationOf(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.explanation")
                        .value("This link shows several risk signals worth a second look."));
    }

    @Test
    @DisplayName("a missing, malformed, expired, or another owner's scan returns the same safe not-found")
    void notFoundScanReturnsSafeNotFound() throws Exception {
        given(explanationService.explain(anyString(), any(UUID.class))).willThrow(new ScanNotFoundException());

        mockMvc.perform(post("/api/v1/scans/{scanId}/explanation", SCAN_ID).principal(authenticationOf(USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"))
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    @DisplayName("a disabled feature or a provider failure returns the one safe unavailable response")
    void unavailableExplanationReturnsSafeError() throws Exception {
        given(explanationService.explain(anyString(), any(UUID.class)))
                .willThrow(new ExplanationUnavailableException());

        mockMvc.perform(post("/api/v1/scans/{scanId}/explanation", SCAN_ID).principal(authenticationOf(USER_ID)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_EXPLANATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("AI explanation is not available right now."))
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    private static UsernamePasswordAuthenticationToken authenticationOf(UUID userId) {
        AuthenticatedUser user = new AuthenticatedUser(userId, "owner@example.com", UUID.randomUUID(), Instant.MAX);
        return new UsernamePasswordAuthenticationToken(user, null);
    }
}
