package com.lyanhkhoa.linksentry.license.persistence;

import com.lyanhkhoa.linksentry.license.domain.License;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA row for one administrator-created license. */
@Entity
@Table(name = "license")
public class LicenseEntity {

    @Id
    @Column(name = "license_id", nullable = false, columnDefinition = "uuid")
    private UUID licenseId;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "max_devices", nullable = false)
    private int maxDevices;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LicenseEntity() {}

    public LicenseEntity(License license) {
        this.licenseId = license.licenseId();
        this.label = license.label();
        this.expiresAt = license.expiresAt();
        this.maxDevices = license.maxDevices();
        this.revokedAt = license.revokedAt();
        this.createdAt = license.createdAt();
    }

    public License toDomain() {
        return new License(licenseId, label, expiresAt, maxDevices, revokedAt, createdAt);
    }

    public void updateExpiry(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void revoke(Instant revokedAt) {
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }
}
