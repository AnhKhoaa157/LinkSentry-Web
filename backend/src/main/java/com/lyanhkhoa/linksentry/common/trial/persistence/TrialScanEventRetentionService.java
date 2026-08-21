package com.lyanhkhoa.linksentry.common.trial.persistence;

import com.lyanhkhoa.linksentry.common.trial.AnonymousTrialProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Required, independent sweep for {@code device_trial_scan_event} rows belonging to a device that
 * has assignment history (so {@code DevicePendingRetentionService.purgeNeverAssigned}'s {@code NOT
 * EXISTS (assignment)} clause never reaches it) but exhausted its trial quota and never returned.
 * Without this sweep those rows would be permanent, since nothing else ever revisits them (ADR
 * 0010, "Retention").
 *
 * <p>Deletes in bounded batches of {@code batchSize} rather than one unbounded statement, so one
 * sweep tick never holds a single huge transaction against a table that spans every device. The
 * cutoff is the same {@code window} the quota itself uses — a stale event is simply one that could
 * no longer count toward any device's quota, not a separately configured retention period.
 */
@Service
public class TrialScanEventRetentionService {

    private static final int BATCH_SIZE = 500;

    private final TrialScanEventBatchDeleter batchDeleter;
    private final AnonymousTrialProperties properties;
    private final Clock clock;

    public TrialScanEventRetentionService(
            TrialScanEventBatchDeleter batchDeleter, AnonymousTrialProperties properties, Clock clock) {
        this.batchDeleter = batchDeleter;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Scheduled hourly in UTC, offset 45 minutes so it never contends with the scan-history
     * retention sweep (offset 0) or {@code DevicePendingRetentionService.purgeNeverAssigned}
     * (offset 30). The public method is also directly callable from tests, so this sweep's
     * behavior never depends on waiting for a scheduler tick.
     */
    @Scheduled(cron = "0 45 * * * *", zone = "UTC")
    public void sweepStaleEvents() {
        Instant cutoff = Instant.now(clock).minus(properties.window());
        int deleted;
        do {
            deleted = batchDeleter.deleteOneBatch(cutoff, BATCH_SIZE);
        } while (deleted == BATCH_SIZE);
    }
}
