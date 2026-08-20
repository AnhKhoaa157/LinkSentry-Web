package com.lyanhkhoa.linksentry.license.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data adapter contract; callers depend on the license domain port instead. */
public interface SpringDataDeviceRepository extends JpaRepository<DeviceEntity, UUID> {

    Optional<DeviceEntity> findByCredentialHash(String credentialHash);

    Optional<DeviceEntity> findByActivationCode(String activationCode);

    // NOT EXISTS, not a revoked-only filter: a device with even one revoked
    // assignment must survive, so the correlated subquery excludes any device
    // that has ever appeared in device_license_assignment at all.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from DeviceEntity d
            where d.createdAt < :cutoff
              and not exists (
                  select 1 from DeviceLicenseAssignmentEntity a where a.deviceId = d.deviceId
              )
            """)
    int deleteNeverAssignedOlderThan(@Param("cutoff") Instant cutoff);
}
