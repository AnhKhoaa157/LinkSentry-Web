package com.lyanhkhoa.linksentry.license.persistence;

import com.lyanhkhoa.linksentry.license.domain.DeviceLicenseAssignment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA row for one grant of a device to a license. */
@Entity
@Table(name = "device_license_assignment")
public class DeviceLicenseAssignmentEntity {

    @Id
    @Column(name = "assignment_id", nullable = false, columnDefinition = "uuid")
    private UUID assignmentId;

    @Column(name = "license_id", nullable = false, columnDefinition = "uuid")
    private UUID licenseId;

    @Column(name = "device_id", nullable = false, columnDefinition = "uuid")
    private UUID deviceId;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected DeviceLicenseAssignmentEntity() {}

    public DeviceLicenseAssignmentEntity(DeviceLicenseAssignment assignment) {
        this.assignmentId = assignment.assignmentId();
        this.licenseId = assignment.licenseId();
        this.deviceId = assignment.deviceId();
        this.grantedAt = assignment.grantedAt();
        this.revokedAt = assignment.revokedAt();
    }

    public DeviceLicenseAssignment toDomain() {
        return new DeviceLicenseAssignment(assignmentId, licenseId, deviceId, grantedAt, revokedAt);
    }

    public void revoke(Instant revokedAt) {
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }
}
