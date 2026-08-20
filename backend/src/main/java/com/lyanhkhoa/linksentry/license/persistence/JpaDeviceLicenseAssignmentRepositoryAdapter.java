package com.lyanhkhoa.linksentry.license.persistence;

import com.lyanhkhoa.linksentry.license.domain.DeviceLicenseAssignment;
import com.lyanhkhoa.linksentry.license.domain.DeviceLicenseAssignmentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** JPA adapter that keeps persistence details out of the license application layer. */
@Repository
public class JpaDeviceLicenseAssignmentRepositoryAdapter implements DeviceLicenseAssignmentRepository {

    private final SpringDataDeviceLicenseAssignmentRepository repository;

    public JpaDeviceLicenseAssignmentRepositoryAdapter(SpringDataDeviceLicenseAssignmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(DeviceLicenseAssignment assignment) {
        repository.save(new DeviceLicenseAssignmentEntity(assignment));
    }

    @Override
    public Optional<DeviceLicenseAssignment> findActiveByDeviceId(UUID deviceId) {
        return repository.findByDeviceIdAndRevokedAtIsNull(deviceId).map(DeviceLicenseAssignmentEntity::toDomain);
    }

    @Override
    public Optional<DeviceLicenseAssignment> findLatestByDeviceId(UUID deviceId) {
        return repository.findFirstByDeviceIdOrderByGrantedAtDesc(deviceId).map(DeviceLicenseAssignmentEntity::toDomain);
    }

    @Override
    public List<DeviceLicenseAssignment> findActiveByLicenseId(UUID licenseId) {
        return repository.findByLicenseIdAndRevokedAtIsNull(licenseId).stream()
                .map(DeviceLicenseAssignmentEntity::toDomain)
                .toList();
    }

    @Override
    public long countActiveByLicenseId(UUID licenseId) {
        return repository.countByLicenseIdAndRevokedAtIsNull(licenseId);
    }

    @Override
    public void revoke(UUID assignmentId, Instant revokedAt) {
        repository.findById(assignmentId).ifPresent(entity -> {
            entity.revoke(revokedAt);
            repository.save(entity);
        });
    }
}
