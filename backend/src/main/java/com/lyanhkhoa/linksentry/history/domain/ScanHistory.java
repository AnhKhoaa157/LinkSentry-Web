package com.lyanhkhoa.linksentry.history.domain;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable safe snapshot of one completed scan.
 *
 * <p>This type deliberately has no {@code originalInput}. It is the only domain
 * object allowed to cross from the scan application service into persistence.
 */
public record ScanHistory(
        UUID scanId,
        String redactedDisplayValue,
        StoredNormalizedUrl normalized,
        int score,
        RiskLevel riskLevel,
        List<StoredFinding> findings,
        String engineVersion,
        Instant analyzedAt) {

    public ScanHistory {
        Objects.requireNonNull(scanId, "scanId");
        Objects.requireNonNull(redactedDisplayValue, "redactedDisplayValue");
        Objects.requireNonNull(normalized, "normalized");
        Objects.requireNonNull(riskLevel, "riskLevel");
        Objects.requireNonNull(findings, "findings");
        Objects.requireNonNull(engineVersion, "engineVersion");
        Objects.requireNonNull(analyzedAt, "analyzedAt");
        findings = List.copyOf(findings);
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be within 0..100");
        }
    }
}
