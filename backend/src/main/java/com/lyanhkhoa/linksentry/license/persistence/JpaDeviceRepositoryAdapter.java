package com.lyanhkhoa.linksentry.license.persistence;

import com.lyanhkhoa.linksentry.license.domain.Device;
import com.lyanhkhoa.linksentry.license.domain.DeviceRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** JPA adapter that keeps persistence details out of the license application layer. */
@Repository
public class JpaDeviceRepositoryAdapter implements DeviceRepository {

    private final SpringDataDeviceRepository repository;

    public JpaDeviceRepositoryAdapter(SpringDataDeviceRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Device device) {
        repository.save(new DeviceEntity(device));
    }

    @Override
    public Optional<Device> findByCredentialHash(String credentialHash) {
        return repository.findByCredentialHash(credentialHash).map(DeviceEntity::toDomain);
    }

    @Override
    public Optional<Device> findByActivationCode(String activationCode) {
        return repository.findByActivationCode(activationCode).map(DeviceEntity::toDomain);
    }

    @Override
    public Optional<Device> findById(UUID deviceId) {
        return repository.findById(deviceId).map(DeviceEntity::toDomain);
    }

    @Override
    public Optional<Device> findByIdForUpdate(UUID deviceId) {
        return repository.findByIdForUpdate(deviceId).map(DeviceEntity::toDomain);
    }

    @Override
    public long deleteNeverAssignedOlderThan(Instant cutoff) {
        return repository.deleteNeverAssignedOlderThan(cutoff);
    }
}
