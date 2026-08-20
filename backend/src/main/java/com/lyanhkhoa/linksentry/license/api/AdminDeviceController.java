package com.lyanhkhoa.linksentry.license.api;

import com.lyanhkhoa.linksentry.license.application.DeviceNotFoundException;
import com.lyanhkhoa.linksentry.license.application.LicenseAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator-only endpoints for inspecting and revoking individual devices. Every route under
 * {@code /api/v1/admin/**} is gated by {@code common.security.AdminApiKeyFilter}, not by this class.
 */
@RestController
@RequestMapping("/api/v1/admin/devices")
@Tag(name = "Admin — Devices", description = "Device inspection and revocation for administrator sessions or operators")
public class AdminDeviceController {

    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final LicenseAdminService licenseAdminService;

    public AdminDeviceController(LicenseAdminService licenseAdminService) {
        this.licenseAdminService = licenseAdminService;
    }

    @GetMapping("/by-code/{activationCode}")
    @Operation(summary = "Look up a device by the activation code an end user copied")
    public DeviceLookupResponse findByActivationCode(@PathVariable String activationCode) {
        return licenseAdminService.findDeviceByActivationCode(activationCode);
    }

    @GetMapping("/{deviceId}")
    @Operation(summary = "Look up a device by its ID")
    public DeviceLookupResponse findById(@PathVariable String deviceId) {
        return licenseAdminService.findDeviceById(parseDeviceId(deviceId));
    }

    @PostMapping("/{deviceId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke one device's current license assignment")
    public void revoke(@PathVariable String deviceId) {
        licenseAdminService.revokeDevice(parseDeviceId(deviceId));
    }

    private static UUID parseDeviceId(String rawDeviceId) {
        if (rawDeviceId == null || !CANONICAL_UUID.matcher(rawDeviceId).matches()) {
            throw new DeviceNotFoundException();
        }
        try {
            return UUID.fromString(rawDeviceId);
        } catch (IllegalArgumentException exception) {
            throw new DeviceNotFoundException();
        }
    }
}
