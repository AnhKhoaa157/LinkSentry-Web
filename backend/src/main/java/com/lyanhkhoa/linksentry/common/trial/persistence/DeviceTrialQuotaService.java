package com.lyanhkhoa.linksentry.common.trial.persistence;

import com.lyanhkhoa.linksentry.common.trial.AnonymousTrialProperties;
import com.lyanhkhoa.linksentry.license.domain.Device;
import com.lyanhkhoa.linksentry.license.domain.DeviceRepository;
import com.lyanhkhoa.linksentry.license.security.DeviceCredentialService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The persistent, device-scoped trial-quota adapter {@code common.trial.AnonymousTrialFilter}
 * calls directly (ADR 0010's "no new service layer needed" recommendation — this narrow adapter
 * plays that role instead).
 *
 * <p>{@link #resolveDeviceId} is a new caller of {@link DeviceRepository#findByCredentialHash},
 * independent of {@code license.application.DeviceService}: it resolves <em>any</em> known device
 * (pending, expired, revoked, or licensed), not only a currently licensed one, because "valid" for
 * trial admission means an existing installation, not licensed authority (ADR 0010 item 1).
 *
 * <p>{@link #tryAdmit} reuses the exact lock-then-check-then-insert shape {@code
 * LicenseAdminService#grantDevice} already uses against the license row, applied to the device row
 * instead: lock, prune anything strictly older than {@code window}, count what remains, insert one
 * more event only if still under {@code maxScans}. Both methods let any database or persistence
 * failure propagate as-is (a {@link org.springframework.dao.DataAccessException} or {@link
 * org.springframework.transaction.TransactionException}) rather than catching it here — {@code
 * AnonymousTrialFilter} is what maps that failure to the fixed {@code 503 TRIAL_QUOTA_UNAVAILABLE}
 * response, the same "caught narrowly around that call only" discipline ADR 0010 specifies.
 */
@Component
public class DeviceTrialQuotaService {

    private final DeviceRepository deviceRepository;
    private final DeviceCredentialService credentialService;
    private final SpringDataDeviceTrialScanEventRepository eventRepository;
    private final Duration window;
    private final int maxScans;

    public DeviceTrialQuotaService(
            DeviceRepository deviceRepository,
            DeviceCredentialService credentialService,
            SpringDataDeviceTrialScanEventRepository eventRepository,
            AnonymousTrialProperties properties) {
        this.deviceRepository = deviceRepository;
        this.credentialService = credentialService;
        this.eventRepository = eventRepository;
        this.window = properties.window();
        this.maxScans = properties.maxScans();
    }

    /**
     * Resolves {@code rawCredential} to a known device id, in any state. Empty means no credential,
     * a malformed one, or one matching no {@code device_installation} row at all — the three cases
     * {@code AnonymousTrialFilter} maps identically to {@code 401 TRIAL_DEVICE_REQUIRED}.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> resolveDeviceId(String rawCredential) {
        if (rawCredential == null || rawCredential.isBlank() || rawCredential.length() > 256) {
            return Optional.empty();
        }
        return deviceRepository.findByCredentialHash(credentialService.sha256(rawCredential)).map(Device::deviceId);
    }

    /**
     * Admits one more trial scan for {@code deviceId} if it is still under {@code maxScans} within
     * the rolling, inclusive window ending at {@code now}. An event exactly {@code window} old still
     * counts; only an event strictly older than {@code now - window} is pruned.
     *
     * @return {@code true} and records the admission; {@code false}, recording nothing, once the
     *     window's quota is exhausted or {@code deviceId} no longer exists (a device deleted between
     *     credential resolution and this call — an unlikely race, handled by denying rather than
     *     granting, matching the fail-closed posture the rest of this quota already takes)
     */
    @Transactional
    public boolean tryAdmit(UUID deviceId, Instant now) {
        Optional<Device> locked = deviceRepository.findByIdForUpdate(deviceId);
        if (locked.isEmpty()) {
            return false;
        }
        Instant cutoff = now.minus(window);
        eventRepository.deleteByDeviceIdAndAdmittedAtBefore(deviceId, cutoff);
        if (eventRepository.countByDeviceId(deviceId) >= maxScans) {
            return false;
        }
        eventRepository.save(new DeviceTrialScanEventEntity(UUID.randomUUID(), deviceId, now));
        return true;
    }
}
