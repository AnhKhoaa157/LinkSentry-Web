package com.lyanhkhoa.linksentry.license.application;

import com.lyanhkhoa.linksentry.license.api.DeviceBootstrapRequest;
import com.lyanhkhoa.linksentry.license.api.DeviceBootstrapResponse;
import com.lyanhkhoa.linksentry.license.api.DeviceStatusResponse;
import com.lyanhkhoa.linksentry.license.domain.Device;
import com.lyanhkhoa.linksentry.license.domain.DeviceLicenseAssignment;
import com.lyanhkhoa.linksentry.license.domain.DeviceLicenseAssignmentRepository;
import com.lyanhkhoa.linksentry.license.domain.DeviceRepository;
import com.lyanhkhoa.linksentry.license.domain.DeviceState;
import com.lyanhkhoa.linksentry.license.domain.License;
import com.lyanhkhoa.linksentry.license.domain.LicenseRepository;
import com.lyanhkhoa.linksentry.license.security.DeviceCredentialService;
import com.lyanhkhoa.linksentry.license.security.LicensedDeviceContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public boundary for device bootstrap, status, and privileged-request authentication.
 *
 * <p>{@link #authenticate(String)} is the only method that ever grants access beyond trial: it returns a
 * value only when the presenting device currently holds an active, non-expired, non-revoked license — a
 * merely {@code PENDING} device (one with a valid credential but no grant) authenticates to nothing here,
 * exactly like an anonymous caller, so a copied activation code alone can never unlock a licensed feature.
 */
@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceLicenseAssignmentRepository assignmentRepository;
    private final LicenseRepository licenseRepository;
    private final DeviceCredentialService credentialService;
    private final Clock clock;

    public DeviceService(
            DeviceRepository deviceRepository,
            DeviceLicenseAssignmentRepository assignmentRepository,
            LicenseRepository licenseRepository,
            DeviceCredentialService credentialService,
            Clock clock) {
        this.deviceRepository = deviceRepository;
        this.assignmentRepository = assignmentRepository;
        this.licenseRepository = licenseRepository;
        this.credentialService = credentialService;
        this.clock = clock;
    }

    /** Creates one new independent installation. The raw credential is returned only here. */
    @Transactional
    public DeviceBootstrapResponse bootstrap(DeviceBootstrapRequest request) {
        String rawCredential = credentialService.newRawCredential();
        String clientLabel = request == null || request.clientLabel() == null || request.clientLabel().isBlank()
                ? null
                : request.clientLabel();
        Device device = new Device(
                UUID.randomUUID(),
                credentialService.newActivationCode(),
                credentialService.sha256(rawCredential),
                clientLabel,
                Instant.now(clock));
        deviceRepository.save(device);
        return new DeviceBootstrapResponse(device.deviceId(), device.activationCode(), rawCredential);
    }

    /** Full status for the device presenting {@code rawCredential}, regardless of whether it is licensed. */
    @Transactional(readOnly = true)
    public DeviceStatusResponse status(String rawCredential) {
        Device device = resolveDevice(rawCredential).orElseThrow(InvalidDeviceCredentialException::new);
        return statusOf(device);
    }

    /**
     * Resolves the licensed identity for a privileged request, or empty when the device is unknown, its
     * credential does not match, or it is not currently licensed. Never throws for an ordinary trial
     * device — that is the expected, common case, not an error.
     */
    @Transactional(readOnly = true)
    public Optional<LicensedDeviceContext> authenticate(String rawCredential) {
        return resolveDevice(rawCredential)
                .flatMap(device -> assignmentRepository.findActiveByDeviceId(device.deviceId()))
                .flatMap(assignment -> licenseRepository.findById(assignment.licenseId())
                        .filter(license -> license.isActive(Instant.now(clock)))
                        .map(license -> new LicensedDeviceContext(assignment.deviceId(), license.licenseId(), license.expiresAt())));
    }

    private Optional<Device> resolveDevice(String rawCredential) {
        if (rawCredential == null || rawCredential.isBlank() || rawCredential.length() > 256) {
            return Optional.empty();
        }
        return deviceRepository.findByCredentialHash(credentialService.sha256(rawCredential));
    }

    private DeviceStatusResponse statusOf(Device device) {
        Optional<DeviceLicenseAssignment> latest = assignmentRepository.findLatestByDeviceId(device.deviceId());
        if (latest.isEmpty()) {
            return new DeviceStatusResponse(DeviceState.PENDING, device.activationCode(), null);
        }
        DeviceLicenseAssignment assignment = latest.get();
        if (!assignment.isActive()) {
            return new DeviceStatusResponse(DeviceState.REVOKED, device.activationCode(), null);
        }
        License license = licenseRepository
                .findById(assignment.licenseId())
                .orElseThrow(() -> new IllegalStateException("Active assignment references a missing license"));
        if (license.isRevoked()) {
            return new DeviceStatusResponse(DeviceState.REVOKED, device.activationCode(), null);
        }
        Instant now = Instant.now(clock);
        if (license.isExpired(now)) {
            return new DeviceStatusResponse(DeviceState.EXPIRED, device.activationCode(), license.expiresAt());
        }
        return new DeviceStatusResponse(DeviceState.LICENSED, device.activationCode(), license.expiresAt());
    }
}
