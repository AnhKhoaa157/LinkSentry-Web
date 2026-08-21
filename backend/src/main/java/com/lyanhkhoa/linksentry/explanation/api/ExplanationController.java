package com.lyanhkhoa.linksentry.explanation.api;

import com.lyanhkhoa.linksentry.explanation.domain.ExplanationResult;
import com.lyanhkhoa.linksentry.license.security.LicensedDeviceContext;
import com.lyanhkhoa.linksentry.explanation.application.ExplanationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for the optional AI explanation of one retained, license-owned scan.
 *
 * <p>{@code SecurityConfig} requires {@code
 * com.lyanhkhoa.linksentry.common.security.DeviceAuthenticationFilter#LICENSED_DEVICE_AUTHORITY} for
 * this route, so {@code authentication} should always carry a {@link LicensedDeviceContext} by the
 * time a request reaches here — a trial scan has no persisted scan ID to invoke this endpoint with in
 * the first place. {@link #requireLicensedDevice} still checks defensively rather than casting: a
 * present-but-wrong-type principal (e.g. {@code admin.domain.AdminIdentity}) must produce a clean
 * {@code 403}, never an unchecked-cast {@code ClassCastException} surfacing as a generic {@code 500}.
 * No analysis logic lives here; this class delegates entirely to {@link ExplanationService}.
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
        LicensedDeviceContext device = requireLicensedDevice(authentication);
        ExplanationResult result = explanationService.explain(scanId, device.licenseId());
        return ExplanationResponse.from(result);
    }

    private static LicensedDeviceContext requireLicensedDevice(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LicensedDeviceContext device) {
            return device;
        }
        throw new AccessDeniedException("A licensed device session is required to generate an explanation.");
    }
}
