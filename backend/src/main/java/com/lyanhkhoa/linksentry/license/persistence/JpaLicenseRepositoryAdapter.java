package com.lyanhkhoa.linksentry.license.persistence;

import com.lyanhkhoa.linksentry.license.domain.License;
import com.lyanhkhoa.linksentry.license.domain.LicenseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** JPA adapter that keeps persistence details out of the license application layer. */
@Repository
public class JpaLicenseRepositoryAdapter implements LicenseRepository {

    private final SpringDataLicenseRepository repository;

    public JpaLicenseRepositoryAdapter(SpringDataLicenseRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(License license) {
        repository.save(new LicenseEntity(license));
    }

    @Override
    public Optional<License> findById(UUID licenseId) {
        return repository.findById(licenseId).map(LicenseEntity::toDomain);
    }

    @Override
    public Optional<License> findByIdForUpdate(UUID licenseId) {
        return repository.findByIdForUpdate(licenseId).map(LicenseEntity::toDomain);
    }

    @Override
    public List<License> findAll() {
        return repository.findAll().stream().map(LicenseEntity::toDomain).toList();
    }

    @Override
    public void updateExpiry(UUID licenseId, Instant expiresAt) {
        repository.findById(licenseId).ifPresent(entity -> {
            entity.updateExpiry(expiresAt);
            repository.save(entity);
        });
    }

    @Override
    public void revoke(UUID licenseId, Instant revokedAt) {
        repository.findById(licenseId).ifPresent(entity -> {
            entity.revoke(revokedAt);
            repository.save(entity);
        });
    }
}
