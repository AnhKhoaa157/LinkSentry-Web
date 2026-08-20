package com.lyanhkhoa.linksentry.license.api;

import com.lyanhkhoa.linksentry.license.application.DeviceService;
import com.lyanhkhoa.linksentry.license.application.InvalidDeviceCredentialException;
import com.lyanhkhoa.linksentry.license.security.DeviceCredentialHeader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for device installation bootstrap and status. Both routes are reachable without a
 * license: bootstrap is how an installation is created in the first place, and status must work for a
 * pending, expired, or revoked device too, not only a licensed one — so neither route is gated by {@code
 * common.security.SecurityConfig}'s {@code .authenticated()} requirement the way licensed-only routes are.
 */
@RestController
@RequestMapping("/api/v1/devices")
@Tag(name = "Devices", description = "Device installation bootstrap and status")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    @Operation(summary = "Create a new independent device installation")
    public DeviceBootstrapResponse bootstrap(@Valid @RequestBody(required = false) DeviceBootstrapRequest request) {
        return deviceService.bootstrap(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Report this device's current state: pending, licensed, expired, or revoked")
    public DeviceStatusResponse me(HttpServletRequest request) {
        String rawCredential = DeviceCredentialHeader.read(request).orElseThrow(InvalidDeviceCredentialException::new);
        return deviceService.status(rawCredential);
    }
}
