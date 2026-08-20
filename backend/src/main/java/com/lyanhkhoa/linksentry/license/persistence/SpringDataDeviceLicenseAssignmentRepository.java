package com.lyanhkhoa.linksentry.license.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data adapter contract; callers depend on the license domain port instead. */
public interface SpringDataDeviceLicenseAssignmentRepository extends JpaRepository<DeviceLicenseAssignmentEntity, UUID> {

    Optional<DeviceLicenseAssignmentEntity> findByDeviceIdAndRevokedAtIsNull(UUID deviceId);

    Optional<DeviceLicenseAssignmentEntity> findFirstByDeviceIdOrderByGrantedAtDesc(UUID deviceId);

    List<DeviceLicenseAssignmentEntity> findByLicenseIdAndRevokedAtIsNull(UUID licenseId);

    long countByLicenseIdAndRevokedAtIsNull(UUID licenseId);
}
