package com.lyanhkhoa.linksentry.history.domain;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import java.util.Objects;
import java.util.Optional;

/** Validated, bounded filters for the private scan-history collection. */
public record ScanHistoryFilter(Optional<RiskLevel> riskLevel, Optional<String> ruleId) {

    public ScanHistoryFilter {
        Objects.requireNonNull(riskLevel, "riskLevel");
        Objects.requireNonNull(ruleId, "ruleId");
        riskLevel = riskLevel.isEmpty() ? Optional.empty() : riskLevel;
        ruleId = ruleId.isEmpty() ? Optional.empty() : ruleId;
    }

    public static ScanHistoryFilter none() {
        return new ScanHistoryFilter(Optional.empty(), Optional.empty());
    }
}
