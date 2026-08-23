package com.lyanhkhoa.linksentry.scan.api;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Privacy-safe response for the licensed scan-history collection endpoint. */
public record ScanHistoryListResponse(Data data, Meta meta) {

    public record Data(List<Item> items, String nextCursor) {
        public Data {
            items = List.copyOf(items);
        }
    }

    public record Item(
            UUID scanId,
            String displayValue,
            String registrableDomain,
            int score,
            RiskLevel riskLevel,
            int findingCount,
            Instant analyzedAt) {}

    public record Meta(int retentionDays) {}
}
