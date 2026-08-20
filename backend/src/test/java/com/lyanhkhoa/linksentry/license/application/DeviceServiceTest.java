package com.lyanhkhoa.linksentry.license.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeviceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final DeviceLicenseAssignmentRepository assignmentRepository = mock(DeviceLicenseAssignmentRepository.class);
    private final LicenseRepository licenseRepository = mock(LicenseRepository.class);
    private final DeviceCredentialService credentialService = new DeviceCredentialService();
    private final DeviceService service =
            new DeviceService(deviceRepository, assignmentRepository, licenseRepository, credentialService, CLOCK);

    @Test
    @DisplayName("bootstrap returns the raw credential and activation code, but persists only the credential's hash")
    void bootstrapPersistsOnlyTheCredentialHash() {
        DeviceBootstrapResponse response = service.bootstrap(new DeviceBootstrapRequest("web"));

        assertThat(response.credential()).isNotBlank();
        assertThat(response.activationCode()).isNotBlank();
        assertThat(response.deviceId()).isNotNull();

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        Device saved = captor.getValue();
        assertThat(saved.credentialHash()).isEqualTo(credentialService.sha256(response.credential()));
        assertThat(saved.credentialHash()).isNotEqualTo(response.credential());
        assertThat(saved.activationCode()).isEqualTo(response.activationCode());
        assertThat(saved.clientLabel()).isEqualTo("web");
    }

    @Test
    @DisplayName("bootstrap tolerates a missing request body and a blank label")
    void bootstrapToleratesMissingRequest() {
        DeviceBootstrapResponse response = service.bootstrap(null);
        assertThat(response.credential()).isNotBlank();

        DeviceBootstrapResponse blankLabel = service.bootstrap(new DeviceBootstrapRequest("  "));
        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getValue().clientLabel()).isNull();
        assertThat(blankLabel).isNotNull();
    }

    @Test
    @DisplayName("status throws InvalidDeviceCredentialException for an unrecognised credential, without leaking it")
    void statusRejectsUnknownCredential() {
        when(deviceRepository.findByCredentialHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.status("garbage-credential")).isInstanceOf(InvalidDeviceCredentialException.class);
    }

    @Test
    @DisplayName("status reports PENDING for a device that was never granted a license")
    void statusReportsPendingForUngrantedDevice() {
        Device device = device();
        when(deviceRepository.findByCredentialHash(any())).thenReturn(Optional.of(device));
        when(assignmentRepository.findLatestByDeviceId(device.deviceId())).thenReturn(Optional.empty());

        DeviceStatusResponse status = service.status("raw-credential");

        assertThat(status.state()).isEqualTo(DeviceState.PENDING);
        assertThat(status.activationCode()).isEqualTo(device.activationCode());
        assertThat(status.licenseExpiresAt()).isNull();
    }

    @Test
    @DisplayName("status reports REVOKED for a device whose most recent assignment was revoked, distinct from PENDING")
    void statusReportsRevokedForRevokedAssignment() {
        Device device = device();
        when(deviceRepository.findByCredentialHash(any())).thenReturn(Optional.of(device));
        DeviceLicenseAssignment revoked = new DeviceLicenseAssignment(
                UUID.randomUUID(), UUID.randomUUID(), device.deviceId(), NOW.minusSeconds(3600), NOW.minusSeconds(60));
        when(assignmentRepository.findLatestByDeviceId(device.deviceId())).thenReturn(Optional.of(revoked));

        DeviceStatusResponse status = service.status("raw-credential");

        assertThat(status.state()).isEqualTo(DeviceState.REVOKED);
        assertThat(status.licenseExpiresAt()).isNull();
    }

    @Test
    @DisplayName("status reports REVOKED when the assignment is active but its license was revoked")
    void statusReportsRevokedForRevokedLicense() {
        Device device = device();
        UUID licenseId = UUID.randomUUID();
        when(deviceRepository.findByCredentialHash(any())).thenReturn(Optional.of(device));
        DeviceLicenseAssignment active =
                new DeviceLicenseAssignment(UUID.randomUUID(), licenseId, device.deviceId(), NOW.minusSeconds(3600), null);
        when(assignmentRepository.findLatestByDeviceId(device.deviceId())).thenReturn(Optional.of(active));
        License revokedLicense = new License(licenseId, "label", null, 2, NOW.minusSeconds(60), NOW.minusSeconds(3600));
        when(licenseRepository.findById(licenseId)).thenReturn(Optional.of(revokedLicense));

        assertThat(service.status("raw-credential").state()).isEqualTo(DeviceState.REVOKED);
    }

    @Test
    @DisplayName("status reports EXPIRED when the assignment is active but its license's expiry has passed")
    void statusReportsExpiredForExpiredLicense() {
        Device device = device();
        UUID licenseId = UUID.randomUUID();
        Instant expiresAt = NOW.minusSeconds(1);
        when(deviceRepository.findByCredentialHash(any())).thenReturn(Optional.of(device));
        DeviceLicenseAssignment active =
                new DeviceLicenseAssignment(UUID.randomUUID(), licenseId, device.deviceId(), NOW.minusSeconds(3600), null);
        when(assignmentRepository.findLatestByDeviceId(device.deviceId())).thenReturn(Optional.of(active));
        License expiredLicense = new License(licenseId, "label", expiresAt, 2, null, NOW.minusSeconds(3600));
        when(licenseRepository.findById(licenseId)).thenReturn(Optional.of(expiredLicense));

        DeviceStatusResponse status = service.status("raw-credential");
        assertThat(status.state()).isEqualTo(DeviceState.EXPIRED);
        assertThat(status.licenseExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("status reports LICENSED for a device with an active assignment to an active, unexpired license")
    void statusReportsLicensedForActiveLicense() {
        Device device = device();
        UUID licenseId = UUID.randomUUID();
        Instant expiresAt = NOW.plusSeconds(3600);
        when(deviceRepository.findByCredentialHash(any())).thenReturn(Optional.of(device));
        DeviceLicenseAssignment active =
                new DeviceLicenseAssignment(UUID.randomUUID(), licenseId, device.deviceId(), NOW.minusSeconds(60), null);
        when(assignmentRepository.findLatestByDeviceId(device.deviceId())).thenReturn(Optional.of(active));
        License activeLicense = new License(licenseId, "label", expiresAt, 2, null, NOW.minusSeconds(3600));
        when(licenseRepository.findById(licenseId)).thenReturn(Optional.of(activeLicense));

        DeviceStatusResponse status = service.status("raw-credential");
        assertThat(status.state()).isEqualTo(DeviceState.LICENSED);
        assertThat(status.licenseExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("status reports LICENSED with a null expiry for a no-expiry license")
    void statusReportsLicensedWithNoExpiry() {
        Device device = device();
        UUID licenseId = UUID.randomUUID();
        when(deviceRepository.findByCredentialHash(any())).thenReturn(Optional.of(device));
        DeviceLicenseAssignment active =
                new DeviceLicenseAssignment(UUID.randomUUID(), licenseId, device.deviceId(), NOW.minusSeconds(60), null);
        when(assignmentRepository.findLatestByDeviceId(device.deviceId())).thenReturn(Optional.of(active));
        License activeLicense = new License(licenseId, "label", null, 2, null, NOW.minusSeconds(3600));
        when(licenseRepository.findById(licenseId)).thenReturn(Optional.of(activeLicense));

        DeviceStatusResponse status = service.status("raw-credential");
        assertThat(status.state()).isEqualTo(DeviceState.LICENSED);
        assertThat(status.licenseExpiresAt()).isNull();
    }

    @Test
    @DisplayName("authenticate returns empty for a null, blank, or oversized credential without touching the repository")
    void authenticateRejectsMalformedCredentialWithoutLookup() {
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("")).isEmpty();
        assertThat(service.authenticate("   ")).isEmpty();
        assertThat(service.authenticate("x".repeat(257))).isEmpty();

        org.mockito.Mockito.verifyNoInteractions(deviceRepository);
    }

    @Test
    @DisplayName("authenticate returns empty for a credential that matches no known device")
    void authenticateRejectsUnknownCredential() {
        when(deviceRepository.findByCredentialHash(any())).thenReturn(Optional.empty());

        assertThat(service.authenticate("unknown-credential")).isEmpty();
    }

    @Test
    @DisplayName("authenticate returns empty for a device with a valid credential but no active license — a copied activation code alone grants nothing, and neither does a bare valid credential without a grant")
    void authenticateRejectsPendingDevice() {
        Device device = device();
        when(deviceRepository.findByCredentialHash(any())).thenReturn(Optional.of(device));
        when(assignmentRepository.findActiveByDeviceId(device.deviceId())).thenReturn(Optional.empty());

        assertThat(service.authenticate("raw-credential")).isEmpty();
    }

    @Test
    @DisplayName("authenticate returns empty for an expired license")
    void authenticateRejectsExpiredLicense() {
        Device device = device();
        UUID licenseId = UUID.randomUUID();
        when(deviceRepository.findByCredentialHash(any())).thenReturn(Optional.of(device));
        DeviceLicenseAssignment active =
                new DeviceLicenseAssignment(UUID.randomUUID(), licenseId, device.deviceId(), NOW.minusSeconds(3600), null);
        when(assignmentRepository.findActiveByDeviceId(device.deviceId())).thenReturn(Optional.of(active));
        License expiredLicense = new License(licenseId, "label", NOW.minusSeconds(1), 2, null, NOW.minusSeconds(3600));
        when(licenseRepository.findById(licenseId)).thenReturn(Optional.of(expiredLicense));

        assertThat(service.authenticate("raw-credential")).isEmpty();
    }

    @Test
    @DisplayName("authenticate returns empty for a revoked license")
    void authenticateRejectsRevokedLicense() {
        Device device = device();
        UUID licenseId = UUID.randomUUID();
        when(deviceRepository.findByCredentialHash(any())).thenReturn(Optional.of(device));
        DeviceLicenseAssignment active =
                new DeviceLicenseAssignment(UUID.randomUUID(), licenseId, device.deviceId(), NOW.minusSeconds(3600), null);
        when(assignmentRepository.findActiveByDeviceId(device.deviceId())).thenReturn(Optional.of(active));
        License revokedLicense = new License(licenseId, "label", null, 2, NOW.minusSeconds(1), NOW.minusSeconds(3600));
        when(licenseRepository.findById(licenseId)).thenReturn(Optional.of(revokedLicense));

        assertThat(service.authenticate("raw-credential")).isEmpty();
    }

    @Test
    @DisplayName("authenticate returns the licensed device context for an active, unexpired, non-revoked license")
    void authenticateAcceptsActiveLicensedDevice() {
        Device device = device();
        UUID licenseId = UUID.randomUUID();
        Instant expiresAt = NOW.plusSeconds(3600);
        when(deviceRepository.findByCredentialHash(any())).thenReturn(Optional.of(device));
        DeviceLicenseAssignment active =
                new DeviceLicenseAssignment(UUID.randomUUID(), licenseId, device.deviceId(), NOW.minusSeconds(60), null);
        when(assignmentRepository.findActiveByDeviceId(device.deviceId())).thenReturn(Optional.of(active));
        License activeLicense = new License(licenseId, "label", expiresAt, 2, null, NOW.minusSeconds(3600));
        when(licenseRepository.findById(licenseId)).thenReturn(Optional.of(activeLicense));

        Optional<LicensedDeviceContext> result = service.authenticate("raw-credential");

        assertThat(result).isPresent();
        assertThat(result.get().deviceId()).isEqualTo(device.deviceId());
        assertThat(result.get().licenseId()).isEqualTo(licenseId);
        assertThat(result.get().licenseExpiresAt()).isEqualTo(expiresAt);
    }

    private static Device device() {
        return new Device(UUID.randomUUID(), "K7H9-QX3P", "irrelevant-hash", null, NOW.minusSeconds(7200));
    }
}
