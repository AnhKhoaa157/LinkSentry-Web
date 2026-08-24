package com.lyanhkhoa.linksentry.history.domain;

import java.util.Objects;

/** Aggregate count for one backend-owned analysis rule. */
public record ScanHistoryFindingCount(String ruleId, long count) {

    public ScanHistoryFindingCount {
        Objects.requireNonNull(ruleId, "ruleId");
        if (ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId must not be blank");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
    }
}
