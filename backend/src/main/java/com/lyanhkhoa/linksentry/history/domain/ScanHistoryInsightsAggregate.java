package com.lyanhkhoa.linksentry.history.domain;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** SQL-produced retained aggregates before API localization and mapping. */
public record ScanHistoryInsightsAggregate(
        long totalScans,
        long scansLast7Days,
        long previous7Days,
        Map<RiskLevel, Long> riskDistribution,
        List<ScanHistoryFindingCount> topFindings) {

    public ScanHistoryInsightsAggregate {
        if (totalScans < 0 || scansLast7Days < 0 || previous7Days < 0) {
            throw new IllegalArgumentException("aggregate counts must not be negative");
        }
        Objects.requireNonNull(riskDistribution, "riskDistribution");
        EnumMap<RiskLevel, Long> completeDistribution = new EnumMap<>(RiskLevel.class);
        for (RiskLevel riskLevel : RiskLevel.values()) {
            Long count = riskDistribution.get(riskLevel);
            if (count == null || count < 0) {
                throw new IllegalArgumentException("riskDistribution must contain non-negative counts for every risk level");
            }
            completeDistribution.put(riskLevel, count);
        }
        if (completeDistribution.values().stream().mapToLong(Long::longValue).sum() != totalScans) {
            throw new IllegalArgumentException("riskDistribution total must equal totalScans");
        }
        riskDistribution = Map.copyOf(completeDistribution);
        Objects.requireNonNull(topFindings, "topFindings");
        if (topFindings.size() > 5) {
            throw new IllegalArgumentException("topFindings must contain at most five items");
        }
        topFindings = List.copyOf(topFindings);
    }
}
