package com.lyanhkhoa.linksentry.history.application;

import com.lyanhkhoa.linksentry.history.domain.ScanHistorySummary;
import java.util.List;

/** One retained-history page plus its opaque continuation cursor. */
public record ScanHistoryPage(List<ScanHistorySummary> items, String nextCursor, int retentionDays) {

    public ScanHistoryPage {
        items = List.copyOf(items);
    }
}
