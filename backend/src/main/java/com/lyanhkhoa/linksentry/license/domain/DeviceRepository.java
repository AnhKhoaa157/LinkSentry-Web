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
