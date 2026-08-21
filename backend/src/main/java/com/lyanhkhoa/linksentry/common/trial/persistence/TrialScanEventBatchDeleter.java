package com.lyanhkhoa.linksentry.common.trial.persistence;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separate bean from {@link TrialScanEventRetentionService} so its one {@code @Transactional}
 * batch delete is invoked through a real Spring proxy. {@code TrialScanEventRetentionService}'s
 * own sweep loop calls this repeatedly; a same-class self-invocation would silently bypass Spring's
 * transactional advice (a self-call never goes through the proxy), and the native {@code @Modifying}
 * delete below requires an active transaction to execute at all.
 */
@Component
class TrialScanEventBatchDeleter {

    private final SpringDataDeviceTrialScanEventRepository eventRepository;

    TrialScanEventBatchDeleter(SpringDataDeviceTrialScanEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    int deleteOneBatch(Instant cutoff, int batchSize) {
        return eventRepository.deleteBatchOlderThan(cutoff, batchSize);
    }
}
