package com.lyanhkhoa.linksentry.license.application;

import com.lyanhkhoa.linksentry.common.config.LicenseProperties;
import com.lyanhkhoa.linksentry.license.api.CreateLicenseRequest;
import com.lyanhkhoa.linksentry.license.api.DeviceLookupResponse;
import com.lyanhkhoa.linksentry.license.api.DeviceSummaryResponse;
import com.lyanhkhoa.linksentry.license.api.LicenseResponse;
import com.lyanhkhoa.linksentry.license.api.LicenseSummaryResponse;
import com.lyanhkhoa.linksentry.license.domain.Device;
import com.lyanhkhoa.linksentry.license.domain.DeviceLicenseAssignment;
import com.lyanhkhoa.linksentry.license.domain.DeviceLicenseAssignmentRepository;
import com.lyanhkhoa.linksentry.license.domain.DeviceRepository;
import com.lyanhkhoa.linksentry.license.domain.DeviceState;
import com.lyanhkhoa.linksentry.license.domain.License;
import com.lyanhkhoa.linksentry.license.domain.LicenseRepository;
import com.lyanhkhoa.linksentry.license.security.DeviceCredentialService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator-only license management. Every method here is reached only through {@code license.api}
 * routes gated by {@code common.security.AdminApiKeyFilter} — this class performs no key check of its
 * own, the same separation {@code ScanService} keeps from rate limiting.
 */
@Service
public class LicenseAdminService {

    private final LicenseRepository licenseRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceLicenseAssignmentRepository assignmentRepository;
    private final LicenseProperties licenseProperties;
    private final Clock clock;

    public LicenseAdminService(
            LicenseRepository licenseRepository,
            DeviceRepository deviceRepository,
            DeviceLicenseAssignmentRepository assignmentRepository,
            LicenseProperties licenseProperties,
            Clock clock) {
        this.licenseRepository = licenseRepository;
        this.deviceRepository = deviceRepository;
        this.assignmentRepository = assignmentRepository;
        this.licenseProperties = licenseProperties;
        this.clock = clock;
    }

    @Transactional
    public LicenseResponse create(CreateLicenseRequest request) {
        int maxDevices = request.maxDevices() != null ? request.maxDevices() : licenseProperties.defaultMaxDevices();
        License license =
                new License(UUID.randomUUID(), request.label(), request.expiresAt(), maxDevices, null, Instant.now(clock));
        licenseRepository.save(license);
        return toResponse(license, List.of());
    }

    @Transactional(readOnly = true)
    public LicenseResponse get(UUID licenseId) {
        License license = licenseRepository.findById(licenseId).orElseThrow(LicenseNotFoundException::new);
        return toResponse(license, assignmentRepository.findActiveByLicenseId(licenseId));
    }

    @Transactional(readOnly = true)
    public List<LicenseSummaryResponse> list() {
        return licenseRepository.findAll().stream()
                .map(license -> new LicenseSummaryResponse(
                        license.licenseId(),
                        license.label(),
                        license.expiresAt(),
                        license.maxDevices(),
                        license.isRevoked(),
                        license.createdAt(),
                        assignmentRepository.countActiveByLicenseId(license.licenseId())))
                .toList();
    }

    @Transactional
    public LicenseResponse extend(UUID licenseId, Instant expiresAt) {
        License license = licenseRepository.findById(licenseId).orElseThrow(LicenseNotFoundException::new);
        licenseRepository.updateExpiry(license.licenseId(), expiresAt);
        return get(licenseId);
    }

    @Transactional
    public void revokeLicense(UUID licenseId) {
        License license = licenseRepository.findById(licenseId).orElseThrow(LicenseNotFoundException::new);
        licenseRepository.revoke(license.licenseId(), Instant.now(clock));
    }

    /**
     * Attaches a pending device's activation code to a license, enforcing its device cap.
     *
     * <p>Uses {@link LicenseRepository#findByIdForUpdate} rather than the plain lookup: this method
     * reads the current active-device count and then inserts a new assignment, and without a lock two
     * concurrent grant requests for the same license could each read the count before either inserts,
     * both pass the cap check, and together oversubscribe {@code maxDevices}. The pessimistic write
     * lock on the license row serializes concurrent grants for that license (see the port's Javadoc for
     * why locking the parent row is sufficient), so the count this method reads is never stale by the
     * time it inserts.
     */
    @Transactional
    public LicenseResponse grantDevice(UUID licenseId, String rawActivationCode) {
        License license = licenseRepository.findByIdForUpdate(licenseId).orElseThrow(LicenseNotFoundException::new);
        if (license.isRevoked()) {
            throw new LicenseRevokedException();
        }
        Device device = deviceRepository
                .findByActivationCode(DeviceCredentialService.normalizeActivationCode(rawActivationCode))
                .orElseThrow(DeviceNotFoundException::new);
        if (assignmentRepository.findActiveByDeviceId(device.deviceId()).isPresent()) {
            throw new DeviceAlreadyAssignedException();
        }
        if (assignmentRepository.countActiveByLicenseId(licenseId) >= license.maxDevices()) {
            throw new DeviceLimitExceededException();
        }
        assignmentRepository.save(
                new DeviceLicenseAssignment(UUID.randomUUID(), licenseId, device.deviceId(), Instant.now(clock), null));
        return get(licenseId);
    }

    /** Revokes only the device's current active assignment; the license and its other devices are unaffected. */
    @Transactional
    public void revokeDevice(UUID deviceId) {
        DeviceLicenseAssignment active =
                assignmentRepository.findActiveByDeviceId(deviceId).orElseThrow(DeviceAssignmentNotFoundException::new);
        assignmentRepository.revoke(active.assignmentId(), Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public DeviceLookupResponse findDeviceByActivationCode(String rawActivationCode) {
        Device device = deviceRepository
                .findByActivationCode(DeviceCredentialService.normalizeActivationCode(rawActivationCode))
                .orElseThrow(DeviceNotFoundException::new);
        return toLookupResponse(device);
    }

    @Transactional(readOnly = true)
    public DeviceLookupResponse findDeviceById(UUID deviceId) {
        Device device = deviceRepository.findById(deviceId).orElseThrow(DeviceNotFoundException::new);
        return toLookupResponse(device);
    }

    private DeviceLookupResponse toLookupResponse(Device device) {
        Optional<DeviceLicenseAssignment> active = assignmentRepository.findActiveByDeviceId(device.deviceId());
        DeviceState state = active.isEmpty() ? deviceStateWithoutActiveAssignment(device.deviceId()) : DeviceState.LICENSED;
        UUID licenseId = active.map(DeviceLicenseAssignment::licenseId).orElse(null);
        return new DeviceLookupResponse(
                device.deviceId(), device.activationCode(), device.clientLabel(), state, licenseId, device.createdAt());
    }

    /**
     * Distinguishes a never-granted device from a revoked one for admin display only; this is a coarser
     * view than {@code DeviceService.status} and deliberately does not evaluate license expiry, since an
     * admin inspecting a device cares whether it is attached and revoked, not the live entitlement math
     * {@code DeviceService} already owns.
     */
    private DeviceState deviceStateWithoutActiveAssignment(UUID deviceId) {
        return assignmentRepository.findLatestByDeviceId(deviceId).isPresent() ? DeviceState.REVOKED : DeviceState.PENDING;
    }

    private LicenseResponse toResponse(License license, List<DeviceLicenseAssignment> activeAssignments) {
        List<DeviceSummaryResponse> devices = activeAssignments.stream()
                .map(assignment -> {
                    Device device = deviceRepository
                            .findById(assignment.deviceId())
                            .orElseThrow(() -> new IllegalStateException("Assignment references a missing device"));
                    return new DeviceSummaryResponse(
                            device.deviceId(), device.activationCode(), device.clientLabel(), assignment.grantedAt());
                })
                .toList();
        return new LicenseResponse(
                license.licenseId(),
                license.label(),
                license.expiresAt(),
                license.maxDevices(),
                license.isRevoked(),
                license.createdAt(),
                devices);
    }
}
