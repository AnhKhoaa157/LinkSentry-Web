package com.lyanhkhoa.linksentry.license.api;

import com.lyanhkhoa.linksentry.license.application.LicenseAdminService;
import com.lyanhkhoa.linksentry.license.application.LicenseNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator-only endpoints for creating and managing licenses. Every route under {@code
 * /api/v1/admin/**} is gated by {@code common.security.AdminApiKeyFilter}, not by this class — a request
 * that reaches a method here has already presented a valid administrator session or {@code ADMIN_API_KEY}.
 */
@RestController
@RequestMapping("/api/v1/admin/licenses")
@Tag(name = "Admin — Licenses", description = "License management for administrator sessions or operators")
public class AdminLicenseController {

    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final LicenseAdminService licenseAdminService;

    public AdminLicenseController(LicenseAdminService licenseAdminService) {
        this.licenseAdminService = licenseAdminService;
    }

    @PostMapping
    @Operation(summary = "Create a license")
    public LicenseResponse create(@Valid @RequestBody CreateLicenseRequest request) {
        return licenseAdminService.create(request);
    }

    @GetMapping
    @Operation(summary = "List every license, without device detail")
    public List<LicenseSummaryResponse> list() {
        return licenseAdminService.list();
    }

    @GetMapping("/{licenseId}")
    @Operation(summary = "Inspect one license and its currently active devices")
    public LicenseResponse get(@PathVariable String licenseId) {
        return licenseAdminService.get(parseLicenseId(licenseId));
    }

    @PostMapping("/{licenseId}/extend")
    @Operation(summary = "Change a license's expiry; a null expiresAt means no expiry")
    public LicenseResponse extend(@PathVariable String licenseId, @Valid @RequestBody ExtendLicenseRequest request) {
        return licenseAdminService.extend(parseLicenseId(licenseId), request.expiresAt());
    }

    @PostMapping("/{licenseId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a license; every device under it loses access on its next request")
    public void revoke(@PathVariable String licenseId) {
        licenseAdminService.revokeLicense(parseLicenseId(licenseId));
    }

    @PostMapping("/{licenseId}/devices")
    @Operation(summary = "Grant a pending device's activation code to this license")
    public LicenseResponse grantDevice(@PathVariable String licenseId, @Valid @RequestBody GrantDeviceRequest request) {
        return licenseAdminService.grantDevice(parseLicenseId(licenseId), request.activationCode());
    }

    private static UUID parseLicenseId(String rawLicenseId) {
        if (rawLicenseId == null || !CANONICAL_UUID.matcher(rawLicenseId).matches()) {
            throw new LicenseNotFoundException();
        }
        try {
            return UUID.fromString(rawLicenseId);
        } catch (IllegalArgumentException exception) {
            throw new LicenseNotFoundException();
        }
    }
}
