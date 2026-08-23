package com.lyanhkhoa.linksentry.history.domain;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Privacy-safe projection used by the licensed scan-history list. */
public record ScanHistorySummary(
        UUID scanId,
        String redactedDisplayValue,
        String registrableDomain,
        int score,
        RiskLevel riskLevel,
        int findingCount,
        Instant analyzedAt) {

    public ScanHistorySummary {
        Objects.requireNonNull(scanId, "scanId");
        Objects.requireNonNull(redactedDisplayValue, "redactedDisplayValue");
        Objects.requireNonNull(riskLevel, "riskLevel");
        Objects.requireNonNull(analyzedAt, "analyzedAt");
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be within 0..100");
        }
        if (findingCount < 0) {
            throw new IllegalArgumentException("findingCount must not be negative");
        }
    }
}
