package com.lyanhkhoa.linksentry.license.persistence;

import com.lyanhkhoa.linksentry.license.domain.Device;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA row for one device installation; no raw credential is represented here. */
@Entity
@Table(name = "device_installation")
public class DeviceEntity {

    @Id
    @Column(name = "device_id", nullable = false, columnDefinition = "uuid")
    private UUID deviceId;

    @Column(name = "activation_code", nullable = false, length = 32, unique = true)
    private String activationCode;

    @Column(name = "credential_hash", nullable = false, length = 64, unique = true)
    private String credentialHash;

    @Column(name = "client_label", length = 32)
    private String clientLabel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DeviceEntity() {}

    public DeviceEntity(Device device) {
        this.deviceId = device.deviceId();
        this.activationCode = device.activationCode();
        this.credentialHash = device.credentialHash();
        this.clientLabel = device.clientLabel();
        this.createdAt = device.createdAt();
    }

    public Device toDomain() {
        return new Device(deviceId, activationCode, credentialHash, clientLabel, createdAt);
    }
}
