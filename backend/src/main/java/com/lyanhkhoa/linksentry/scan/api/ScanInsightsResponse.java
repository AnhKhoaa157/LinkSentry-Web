package com.lyanhkhoa.linksentry.scan.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import java.util.List;
import java.util.Map;

/** Privacy-safe response for retained, license-scoped personal scan insights. */
public record ScanInsightsResponse(Data data, Meta meta) {

    @JsonPropertyOrder({
        "windowDays",
        "totalScans",
        "scansLast7Days",
        "previous7Days",
        "riskDistribution",
        "topFindings"
    })
    public record Data(
            int windowDays,
            long totalScans,
            long scansLast7Days,
            long previous7Days,
            Map<RiskLevel, Long> riskDistribution,
            List<TopFinding> topFindings) {

        public Data {
            riskDistribution = Map.copyOf(riskDistribution);
            topFindings = List.copyOf(topFindings);
        }
    }

    public record TopFinding(String ruleId, String title, long count) {}

    public record Meta(int retentionDays) {}
}
