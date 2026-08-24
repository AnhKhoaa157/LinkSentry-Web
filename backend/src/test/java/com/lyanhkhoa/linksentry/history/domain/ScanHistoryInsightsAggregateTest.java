package com.lyanhkhoa.linksentry.history.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScanHistoryInsightsAggregateTest {

    @Test
    @DisplayName("risk-distribution counts must add up to total scans")
    void distributionMustMatchTotal() {
        assertThatThrownBy(() -> new ScanHistoryInsightsAggregate(
                        2,
                        0,
                        0,
                        Map.of(
                                RiskLevel.LOW, 1L,
                                RiskLevel.MODERATE, 0L,
                                RiskLevel.HIGH, 0L,
                                RiskLevel.CRITICAL, 0L),
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("riskDistribution total must equal totalScans");
    }

    @Test
    @DisplayName("top findings are bounded to five aggregate rows")
    void topFindingsAreBounded() {
        List<ScanHistoryFindingCount> findings = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> new ScanHistoryFindingCount("RULE_" + index, 1))
                .toList();

        assertThatThrownBy(() -> new ScanHistoryInsightsAggregate(
                        0,
                        0,
                        0,
                        Map.of(
                                RiskLevel.LOW, 0L,
                                RiskLevel.MODERATE, 0L,
                                RiskLevel.HIGH, 0L,
                                RiskLevel.CRITICAL, 0L),
                        findings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topFindings must contain at most five items");
    }
}
