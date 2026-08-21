package com.lyanhkhoa.linksentry.common.trial.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data adapter contract; callers depend on {@link DeviceTrialQuotaService} instead. */
interface SpringDataDeviceTrialScanEventRepository extends JpaRepository<DeviceTrialScanEventEntity, UUID> {

    // Strictly less-than: the boundary is inclusive, so an event exactly `cutoff` old
    // (admittedAt == cutoff) is never pruned — the same rolling-window semantic the
    // retired in-memory heap store used before ADR 0010.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DeviceTrialScanEventEntity e where e.deviceId = :deviceId and e.admittedAt < :cutoff")
    int deleteByDeviceIdAndAdmittedAtBefore(@Param("deviceId") UUID deviceId, @Param("cutoff") Instant cutoff);

    long countByDeviceId(UUID deviceId);

    // Bounded batch for the stale-event sweep (common.trial.persistence.TrialScanEventRetentionService):
    // deletes at most batchSize rows at a time so one sweep tick never holds a single huge
    // transaction against a table that spans every device, unlike the per-device prune above.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
            DELETE FROM device_trial_scan_event
            WHERE event_id IN (
                SELECT event_id FROM device_trial_scan_event
                WHERE admitted_at < :cutoff
                LIMIT :batchSize
            )
            """,
            nativeQuery = true)
    int deleteBatchOlderThan(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
