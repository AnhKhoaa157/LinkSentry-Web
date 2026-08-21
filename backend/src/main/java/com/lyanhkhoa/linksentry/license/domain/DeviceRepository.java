package com.lyanhkhoa.linksentry.license.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for device installations. */
public interface DeviceRepository {

    void save(Device device);

    Optional<Device> findByCredentialHash(String credentialHash);

    Optional<Device> findByActivationCode(String activationCode);

    Optional<Device> findById(UUID deviceId);

    /**
     * Same lookup as {@link #findById}, but holds a database-level pessimistic write lock on the device
     * row until the enclosing transaction ends. {@code common.trial.persistence.DeviceTrialQuotaService}
     * uses this instead of {@link #findById} for the same reason {@code LicenseAdminService#grantDevice}
     * locks the license row: it reads a count derived from this device's child rows
     * ({@code device_trial_scan_event}) and then conditionally inserts one more, and without a lock two
     * concurrent trial-admission requests for the same device could each read the count before either
     * inserts, both pass the {@code maxScans} check, and together oversubscribe the quota. Read-only
     * callers must keep using the unlocked {@link #findById}.
     */
    Optional<Device> findByIdForUpdate(UUID deviceId);

    /**
     * Deletes devices created strictly before {@code cutoff} that have never had any
     * {@code device_license_assignment} row at all — never granted, so never licensed and never
     * revoked either. A device with any assignment history, active or revoked, is never a candidate
     * here regardless of age: that history has audit value this cleanup must not destroy. Bounds
     * unbounded growth from the public {@code POST /api/v1/devices} bootstrap endpoint.
     *
     * @return the number of deleted rows
     */
    long deleteNeverAssignedOlderThan(Instant cutoff);
}
