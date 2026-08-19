package com.lyanhkhoa.linksentry.explanation.api;

import com.lyanhkhoa.linksentry.auth.security.AuthenticatedUser;
import com.lyanhkhoa.linksentry.explanation.api.ExplanationResponse.ExplanationData;
import com.lyanhkhoa.linksentry.explanation.application.ExplanationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for the optional AI explanation of one retained, owned scan.
 *
 * <p>{@code SecurityConfig} requires authentication for this route, so
 * {@code authentication} always carries an {@link AuthenticatedUser} by the time
 * a request reaches here — an anonymous scan has no persisted scan ID to invoke
 * this endpoint with in the first place. No analysis logic lives here: this
 * class validates nothing beyond what Spring Security already guarantees and
 * delegates entirely to {@link ExplanationService}.
 */
@RestController
@RequestMapping("/api/v1/scans")
@Tag(name = "Explanation", description = "Optional AI explanation of a retained scan result")
public class ExplanationController {

    private final ExplanationService explanationService;

    public ExplanationController(ExplanationService explanationService) {
        this.explanationService = explanationService;
    }

    @PostMapping("/{scanId}/explanation")
    @Operation(summary = "Generate a short, advisory AI explanation of a retained scan result")
    public ExplanationResponse explain(@PathVariable String scanId, Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        String explanation = explanationService.explain(scanId, user.userId());
        return new ExplanationResponse(new ExplanationData(explanation));
    }
}
