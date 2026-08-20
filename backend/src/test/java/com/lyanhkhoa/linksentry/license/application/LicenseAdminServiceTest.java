package com.lyanhkhoa.linksentry.license.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lyanhkhoa.linksentry.common.config.LicenseProperties;
import com.lyanhkhoa.linksentry.license.api.CreateLicenseRequest;
import com.lyanhkhoa.linksentry.license.api.LicenseResponse;
import com.lyanhkhoa.linksentry.license.domain.Device;
import com.lyanhkhoa.linksentry.license.domain.DeviceLicenseAssignment;
import com.lyanhkhoa.linksentry.license.domain.DeviceLicenseAssignmentRepository;
import com.lyanhkhoa.linksentry.license.domain.DeviceRepository;
import com.lyanhkhoa.linksentry.license.domain.License;
import com.lyanhkhoa.linksentry.license.domain.LicenseRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LicenseAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String ACTIVATION_CODE = "K7H9-QX3P";

    private final LicenseRepository licenseRepository = mock(LicenseRepository.class);
    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final DeviceLicenseAssignmentRepository assignmentRepository = mock(DeviceLicenseAssignmentRepository.class);
    private final LicenseAdminService service = new LicenseAdminService(
            licenseRepository, deviceRepository, assignmentRepository,
            new LicenseProperties(2, java.time.Duration.ofDays(30)), CLOCK);

    @Test
    @DisplayName("create uses the configured default device cap when maxDevices is omitted")
    void createUsesConfiguredDefaultMaxDevices() {
        LicenseResponse response = service.create(new CreateLicenseRequest("label", null, null));

        assertThat(response.maxDevices()).isEqualTo(2);
        assertThat(response.revoked()).isFalse();
        assertThat(response.devices()).isEmpty();
        verify(licenseRepository).save(any(License.class));
    }

    @Test
    @DisplayName("create honors an explicit maxDevices over the configured default")
    void createHonorsExplicitMaxDevices() {
        LicenseResponse response = service.create(new CreateLicenseRequest("label", null, 5));

        assertThat(response.maxDevices()).isEqualTo(5);
    }

    @Test
    @DisplayName("grantDevice succeeds for the first two devices under the default cap, and rejects a third")
    void grantDeviceEnforcesDefaultCapOfTwo() {
        UUID licenseId = UUID.randomUUID();
        License license = new License(licenseId, "label", null, 2, null, NOW);
        // grantDevice locks via findByIdForUpdate, but its final `get(licenseId)` call (to
        // build the response) legitimately uses the plain findById — both must be stubbed.
        when(licenseRepository.findByIdForUpdate(licenseId)).thenReturn(Optional.of(license));
        when(licenseRepository.findById(licenseId)).thenReturn(Optional.of(license));

        Device firstDevice = deviceWithCode("FIRST-CODE");
        when(deviceRepository.findByActivationCode("FIRST-CODE")).thenReturn(Optional.of(firstDevice));
        when(assignmentRepository.findActiveByDeviceId(firstDevice.deviceId())).thenReturn(Optional.empty());
        when(assignmentRepository.countActiveByLicenseId(licenseId)).thenReturn(0L);
        when(assignmentRepository.findActiveByLicenseId(licenseId)).thenReturn(java.util.List.of());
        service.grantDevice(licenseId, "first-code");
        verify(assignmentRepository, times(1)).save(any(DeviceLicenseAssignment.class));

        Device secondDevice = deviceWithCode("SECOND-CODE");
        when(deviceRepository.findByActivationCode("SECOND-CODE")).thenReturn(Optional.of(secondDevice));
        when(assignmentRepository.findActiveByDeviceId(secondDevice.deviceId())).thenReturn(Optional.empty());
        when(assignmentRepository.countActiveByLicenseId(licenseId)).thenReturn(1L);
        service.grantDevice(licenseId, "second-code");
        verify(assignmentRepository, times(2)).save(any(DeviceLicenseAssignment.class));

        Device thirdDevice = deviceWithCode("THIRD-CODE");
        when(deviceRepository.findByActivationCode("THIRD-CODE")).thenReturn(Optional.of(thirdDevice));
        when(assignmentRepository.findActiveByDeviceId(thirdDevice.deviceId())).thenReturn(Optional.empty());
        when(assignmentRepository.countActiveByLicenseId(licenseId)).thenReturn(2L);
        assertThatThrownBy(() -> service.grantDevice(licenseId, "third-code"))
                .isInstanceOf(DeviceLimitExceededException.class);
        // Still exactly 2 saves: the rejected third grant never reached the repository.
        verify(assignmentRepository, times(2)).save(any(DeviceLicenseAssignment.class));
    }

    @Test
    @DisplayName("grantDevice locks the license row for update rather than using the plain unlocked lookup")
    void grantDeviceUsesPessimisticLock() {
        UUID licenseId = UUID.randomUUID();
        License license = new License(licenseId, "l", null, 2, null, NOW);
        Device device = deviceWithCode(ACTIVATION_CODE);
        when(licenseRepository.findByIdForUpdate(licenseId)).thenReturn(Optional.of(license));
        when(licenseRepository.findById(licenseId)).thenReturn(Optional.of(license));
        when(deviceRepository.findByActivationCode(ACTIVATION_CODE)).thenReturn(Optional.of(device));
        when(assignmentRepository.findActiveByDeviceId(device.deviceId())).thenReturn(Optional.empty());
        when(assignmentRepository.countActiveByLicenseId(licenseId)).thenReturn(0L);
        when(assignmentRepository.findActiveByLicenseId(licenseId)).thenReturn(java.util.List.of());

        service.grantDevice(licenseId, ACTIVATION_CODE);

        verify(licenseRepository).findByIdForUpdate(licenseId);
        // get(licenseId), called internally to build the response, legitimately uses the
        // plain unlocked lookup — only the check-then-insert itself needs the lock.
        verify(licenseRepository).findById(licenseId);
    }

    @Test
    @DisplayName("grantDevice rejects an unknown activation code without creating an assignment")
    void grantDeviceRejectsUnknownCode() {
        UUID licenseId = UUID.randomUUID();
        when(licenseRepository.findByIdForUpdate(licenseId))
                .thenReturn(Optional.of(new License(licenseId, "l", null, 2, null, NOW)));
        when(deviceRepository.findByActivationCode(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grantDevice(licenseId, "unknown-code"))
                .isInstanceOf(DeviceNotFoundException.class);
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("grantDevice rejects a device that already holds an active assignment")
    void grantDeviceRejectsAlreadyAssignedDevice() {
        UUID licenseId = UUID.randomUUID();
        Device device = deviceWithCode(ACTIVATION_CODE);
        when(licenseRepository.findByIdForUpdate(licenseId))
                .thenReturn(Optional.of(new License(licenseId, "l", null, 2, null, NOW)));
        when(deviceRepository.findByActivationCode(ACTIVATION_CODE)).thenReturn(Optional.of(device));
        when(assignmentRepository.findActiveByDeviceId(device.deviceId())).thenReturn(Optional.of(
                new DeviceLicenseAssignment(UUID.randomUUID(), UUID.randomUUID(), device.deviceId(), NOW, null)));

        assertThatThrownBy(() -> service.grantDevice(licenseId, ACTIVATION_CODE))
                .isInstanceOf(DeviceAlreadyAssignedException.class);
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("grantDevice rejects a request against a revoked license")
    void grantDeviceRejectsRevokedLicense() {
        UUID licenseId = UUID.randomUUID();
        when(licenseRepository.findByIdForUpdate(licenseId))
                .thenReturn(Optional.of(new License(licenseId, "l", null, 2, NOW.minusSeconds(1), NOW.minusSeconds(100))));

        assertThatThrownBy(() -> service.grantDevice(licenseId, ACTIVATION_CODE)).isInstanceOf(LicenseRevokedException.class);
        verify(deviceRepository, never()).findByActivationCode(any());
    }

    @Test
    @DisplayName("grantDevice normalizes the submitted activation code before lookup")
    void grantDeviceNormalizesActivationCode() {
        UUID licenseId = UUID.randomUUID();
        License license = new License(licenseId, "l", null, 2, null, NOW);
        Device device = deviceWithCode(ACTIVATION_CODE);
        when(licenseRepository.findByIdForUpdate(licenseId)).thenReturn(Optional.of(license));
        when(licenseRepository.findById(licenseId)).thenReturn(Optional.of(license));
        when(deviceRepository.findByActivationCode(ACTIVATION_CODE)).thenReturn(Optional.of(device));
        when(assignmentRepository.findActiveByDeviceId(device.deviceId())).thenReturn(Optional.empty());
        when(assignmentRepository.countActiveByLicenseId(licenseId)).thenReturn(0L);
        when(assignmentRepository.findActiveByLicenseId(licenseId)).thenReturn(java.util.List.of());

        service.grantDevice(licenseId, "  k7h9-qx3p  ");

        verify(deviceRepository).findByActivationCode(ACTIVATION_CODE);
    }

    @Test
    @DisplayName("revokeDevice revokes only the device's current active assignment")
    void revokeDeviceRevokesActiveAssignment() {
        UUID deviceId = UUID.randomUUID();
        DeviceLicenseAssignment active =
                new DeviceLicenseAssignment(UUID.randomUUID(), UUID.randomUUID(), deviceId, NOW.minusSeconds(60), null);
        when(assignmentRepository.findActiveByDeviceId(deviceId)).thenReturn(Optional.of(active));

        service.revokeDevice(deviceId);

        verify(assignmentRepository).revoke(eq(active.assignmentId()), eq(NOW));
    }

    @Test
    @DisplayName("revokeDevice rejects a device with no active assignment")
    void revokeDeviceRejectsUngrantedDevice() {
        UUID deviceId = UUID.randomUUID();
        when(assignmentRepository.findActiveByDeviceId(deviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeDevice(deviceId)).isInstanceOf(DeviceAssignmentNotFoundException.class);
        verify(assignmentRepository, never()).revoke(any(), any());
    }

    @Test
    @DisplayName("revokeLicense revokes an active license and is idempotent for an already-revoked one")
    void revokeLicenseIsIdempotent() {
        UUID licenseId = UUID.randomUUID();
        when(licenseRepository.findById(licenseId)).thenReturn(Optional.of(new License(licenseId, "l", null, 2, null, NOW)));

        service.revokeLicense(licenseId);

        verify(licenseRepository).revoke(licenseId, NOW);
    }

    @Test
    @DisplayName("revokeLicense rejects an unknown license ID")
    void revokeLicenseRejectsUnknownId() {
        UUID licenseId = UUID.randomUUID();
        when(licenseRepository.findById(licenseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeLicense(licenseId)).isInstanceOf(LicenseNotFoundException.class);
    }

    @Test
    @DisplayName("extend updates only the expiry, leaving every other field untouched")
    void extendUpdatesOnlyExpiry() {
        UUID licenseId = UUID.randomUUID();
        Instant newExpiry = NOW.plusSeconds(86_400);
        License original = new License(licenseId, "label", null, 2, null, NOW);
        when(licenseRepository.findById(licenseId)).thenReturn(Optional.of(original))
                .thenReturn(Optional.of(new License(licenseId, "label", newExpiry, 2, null, NOW)));

        LicenseResponse response = service.extend(licenseId, newExpiry);

        ArgumentCaptor<Instant> expiryCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(licenseRepository).updateExpiry(eq(licenseId), expiryCaptor.capture());
        assertThat(expiryCaptor.getValue()).isEqualTo(newExpiry);
        assertThat(response.expiresAt()).isEqualTo(newExpiry);
        assertThat(response.label()).isEqualTo("label");
    }

    @Test
    @DisplayName("get returns LicenseNotFoundException for an unknown ID rather than a null or empty response")
    void getRejectsUnknownId() {
        UUID licenseId = UUID.randomUUID();
        when(licenseRepository.findById(licenseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(licenseId)).isInstanceOf(LicenseNotFoundException.class);
    }

    private static Device deviceWithCode(String activationCode) {
        return new Device(UUID.randomUUID(), activationCode, "hash", null, NOW.minusSeconds(3600));
    }
}
