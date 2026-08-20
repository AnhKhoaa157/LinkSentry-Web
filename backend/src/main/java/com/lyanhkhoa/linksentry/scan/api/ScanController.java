package com.lyanhkhoa.linksentry.scan.api;

import com.lyanhkhoa.linksentry.license.security.LicensedDeviceContext;
import com.lyanhkhoa.linksentry.scan.application.ScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/**
 * HTTP boundary for {@code POST /api/v1/scans} and retained scan retrieval.
 *
 * <p>Validates, delegates to {@link ScanService}, and returns its result. No
 * analysis logic lives here — a malformed or unsupported URL results in a
 * {@code 400 INVALID_URL} response via
 * {@link com.lyanhkhoa.linksentry.common.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/scans")
@Tag(name = "Scans", description = "URL risk analysis and retained results")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping
    @Operation(summary = "Analyse a URL and return its explainable risk score")
    public ScanResponse scan(@Valid @RequestBody ScanRequest request, Authentication authentication) {
        // Anonymous and licensed-device callers are both legitimate here — this route is
        // `permitAll()`. Any other authenticated principal (e.g. an administrator's own session)
        // is treated exactly like an anonymous caller: it grants no scan privilege of its own, so
        // there is nothing to reject, unlike the strictly device-scoped routes below.
        LicensedDeviceContext device = optionalLicensedDevice(authentication);
        return device == null ? scanService.scan(request.url()) : scanService.scan(request.url(), device.licenseId());
    }

    @GetMapping("/{scanId}")
    @Operation(summary = "Retrieve a retained scan result by opaque scan ID")
    public ScanResponse get(@PathVariable String scanId, Authentication authentication) {
        LicensedDeviceContext device = requireLicensedDeviceForLookup(authentication);
        return device == null ? scanService.get(scanId) : scanService.get(scanId, device.licenseId());
    }

    /**
     * Tolerant lookup used only by {@link #scan}, where any non-device principal — including no
     * principal at all — is a legitimate anonymous caller.
     */
    private static LicensedDeviceContext optionalLicensedDevice(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LicensedDeviceContext device) {
            return device;
        }
        return null;
    }

    /**
     * Strict lookup used only by {@link #get}. {@code SecurityConfig} already requires {@link
     * com.lyanhkhoa.linksentry.common.security.DeviceAuthenticationFilter#LICENSED_DEVICE_AUTHORITY}
     * for this route, so a present-but-wrong-type principal (e.g. {@code admin.domain.AdminIdentity})
     * should be structurally unreachable here; this defends the case anyway rather than silently
     * treating a real, non-device credential as an anonymous caller, which would mask the actual
     * authorization boundary. {@code authentication == null} still falls through to {@code null} — the
     * same tolerant behaviour {@link #scan} uses — since a genuinely anonymous request is also rejected
     * earlier by {@code SecurityConfig} in the real chain; the controller does not need to duplicate
     * that decision for the "no credential presented at all" case.
     */
    private static LicensedDeviceContext requireLicensedDeviceForLookup(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof LicensedDeviceContext device) {
            return device;
        }
        throw new AccessDeniedException("A licensed device session is required to retrieve a retained scan.");
    }
}
