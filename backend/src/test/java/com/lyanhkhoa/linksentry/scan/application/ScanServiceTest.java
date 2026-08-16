package com.lyanhkhoa.linksentry.scan.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisResult;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import com.lyanhkhoa.linksentry.common.config.EngineProperties;
import com.lyanhkhoa.linksentry.scan.api.ScanResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScanServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-16T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @Test
    @DisplayName("stamps a fresh scan id, the clock's instant, and the configured engine version")
    void stampsScanIdentity() {
        ScanService service = new ScanService(fixedAnalyzer(), new EngineProperties("0.1.0"), fixedClock);

        ScanResponse response = service.scan("https://example.com/reset-password?token=secret");

        assertThat(response.data().scanId()).isNotNull();
        assertThat(response.data().analyzedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(response.meta().engineVersion()).isEqualTo("0.1.0");
    }

    @Test
    @DisplayName("two scans of the same input get different scan ids")
    void scanIdsAreUnique() {
        ScanService service = new ScanService(fixedAnalyzer(), new EngineProperties("0.1.0"), fixedClock);

        ScanResponse first = service.scan("https://example.com/");
        ScanResponse second = service.scan("https://example.com/");

        assertThat(first.data().scanId()).isNotEqualTo(second.data().scanId());
    }

    @Test
    @DisplayName("the response carries only the redacted display value, never the raw submission")
    void responseCarriesOnlyRedactedValue() {
        ScanService service = new ScanService(fixedAnalyzer(), new EngineProperties("0.1.0"), fixedClock);

        ScanResponse response = service.scan("https://example.com/reset-password?token=secret");

        assertThat(response.data().input()).isEqualTo("https://example.com/reset-password");
        assertThat(response.data().input()).doesNotContain("token=secret");
    }

    @Test
    @DisplayName("findings and score pass through from the analyzer unchanged")
    void findingsAndScorePassThrough() {
        ScanService service = new ScanService(fixedAnalyzer(), new EngineProperties("0.1.0"), fixedClock);

        ScanResponse response = service.scan("https://example.com/reset-password?token=secret");

        assertThat(response.data().score()).isEqualTo(20);
        assertThat(response.data().riskLevel()).isEqualTo(RiskLevel.MODERATE);
        assertThat(response.data().findings()).hasSize(1);
        assertThat(response.data().findings().get(0).ruleId()).isEqualTo("MISSING_HTTPS");
    }

    private static UrlAnalyzer fixedAnalyzer() {
        return rawInput -> {
            NormalizedUrl normalizedUrl = new NormalizedUrl(
                    rawInput,
                    "https://example.com/reset-password",
                    "https",
                    "example.com",
                    "example.com",
                    "example.com",
                    List.of(),
                    null,
                    "/reset-password",
                    true,
                    false,
                    false);
            RuleFinding finding =
                    RuleFinding.of("MISSING_HTTPS", Severity.LOW, 20, "title", "explanation");
            return new AnalysisResult(normalizedUrl, List.of(finding), 20, RiskLevel.MODERATE);
        };
    }
}
