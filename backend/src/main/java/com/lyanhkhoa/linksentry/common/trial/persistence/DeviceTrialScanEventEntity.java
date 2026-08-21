package com.lyanhkhoa.linksentry.common.trial.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One admitted trial scan for one device. No raw URL, credential, remote address, query,
 * fragment, or user-agent is ever represented here — only the three columns below.
 */
@Entity
@Table(name = "device_trial_scan_event")
public class DeviceTrialScanEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, columnDefinition = "uuid")
    private UUID eventId;

    @Column(name = "device_id", nullable = false, columnDefinition = "uuid")
    private UUID deviceId;

    @Column(name = "admitted_at", nullable = false)
    private Instant admittedAt;

    protected DeviceTrialScanEventEntity() {}

    public DeviceTrialScanEventEntity(UUID eventId, UUID deviceId, Instant admittedAt) {
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.admittedAt = admittedAt;
    }

    public UUID eventId() {
        return eventId;
    }

    public UUID deviceId() {
        return deviceId;
    }

    public Instant admittedAt() {
        return admittedAt;
    }
}
