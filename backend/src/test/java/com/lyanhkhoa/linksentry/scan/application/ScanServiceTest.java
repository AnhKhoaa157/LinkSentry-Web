package com.lyanhkhoa.linksentry.scan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisResult;
import com.lyanhkhoa.linksentry.analysis.domain.DomainFeatures;
import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import com.lyanhkhoa.linksentry.common.config.EngineProperties;
import com.lyanhkhoa.linksentry.history.application.ScanHistoryService;
import com.lyanhkhoa.linksentry.history.application.ScanNotFoundException;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.StoredFinding;
import com.lyanhkhoa.linksentry.history.domain.StoredNormalizedUrl;
import com.lyanhkhoa.linksentry.scan.api.ScanResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScanServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-16T12:00:00Z");
    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @Test
    @DisplayName("stamps a fresh scan id, the clock's instant, and the configured engine version")
    void stampsScanIdentity() {
        ScanService service = new ScanService(
                fixedAnalyzer(), new EngineProperties("0.1.0"), fixedClock, mock(ScanHistoryService.class));

        ScanResponse response = service.scan("https://example.com/reset-password?token=secret", OWNER_ID);

        assertThat(response.data().scanId()).isNotNull();
        assertThat(response.data().analyzedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(response.meta().engineVersion()).isEqualTo("0.1.0");
    }

    @Test
    @DisplayName("two scans of the same input get different scan ids")
    void scanIdsAreUnique() {
        ScanService service = new ScanService(
                fixedAnalyzer(), new EngineProperties("0.1.0"), fixedClock, mock(ScanHistoryService.class));

        ScanResponse first = service.scan("https://example.com/", OWNER_ID);
        ScanResponse second = service.scan("https://example.com/", OWNER_ID);

        assertThat(first.data().scanId()).isNotEqualTo(second.data().scanId());
    }

    @Test
    @DisplayName("the response carries only the redacted display value, never the raw submission")
    void responseCarriesOnlyRedactedValue() {
        ScanService service = new ScanService(
                fixedAnalyzer(), new EngineProperties("0.1.0"), fixedClock, mock(ScanHistoryService.class));

        ScanResponse response = service.scan("https://example.com/reset-password?token=secret", OWNER_ID);

        assertThat(response.data().input()).isEqualTo("https://example.com/reset-password");
        assertThat(response.data().input()).doesNotContain("token=secret");
    }

    @Test
    @DisplayName("findings and score pass through from the analyzer unchanged")
    void findingsAndScorePassThrough() {
        ScanService service = new ScanService(
                fixedAnalyzer(), new EngineProperties("0.1.0"), fixedClock, mock(ScanHistoryService.class));

        ScanResponse response = service.scan("https://example.com/reset-password?token=secret", OWNER_ID);

        assertThat(response.data().score()).isEqualTo(20);
        assertThat(response.data().riskLevel()).isEqualTo(RiskLevel.MODERATE);
        assertThat(response.data().findings()).hasSize(1);
        assertThat(response.data().findings().get(0).ruleId()).isEqualTo("MISSING_HTTPS");
    }

    @Test
    @DisplayName("saves exactly one history snapshot after a successful scan")
    void savesExactlyOnceAfterSuccessfulAnalysis() {
        ScanHistoryService historyService = mock(ScanHistoryService.class);
        ScanService service =
                new ScanService(fixedAnalyzer(), new EngineProperties("0.1.0"), fixedClock, historyService);

        service.scan("https://example.com/reset-password?token=secret", OWNER_ID);

        verify(historyService, times(1)).save(any(ScanHistory.class));
    }

    @Test
    @DisplayName("never saves when analysis throws")
    void neverSavesWhenAnalysisThrows() {
        ScanHistoryService historyService = mock(ScanHistoryService.class);
        UrlAnalyzer throwingAnalyzer = rawInput -> {
            throw new InvalidUrlException("Only http and https are supported");
        };
        ScanService service =
                new ScanService(throwingAnalyzer, new EngineProperties("0.1.0"), fixedClock, historyService);

        assertThatThrownBy(() -> service.scan("javascript:alert(1)")).isInstanceOf(InvalidUrlException.class);

        verifyNoInteractions(historyService);
    }

    @Test
    @DisplayName("the saved snapshot carries only safe fields and preserves finding order, "
            + "engine version, timestamp, score, and risk level")
    void savedSnapshotIsSafeAndPreservesOrderAndMetadata() {
        ScanHistoryService historyService = mock(ScanHistoryService.class);
        ScanService service = new ScanService(
                multiFindingAnalyzer(), new EngineProperties("0.1.0"), fixedClock, historyService);

        ScanResponse response = service.scan("https://example.com/reset-password?token=secret#frag", OWNER_ID);

        ArgumentCaptor<ScanHistory> captor = ArgumentCaptor.forClass(ScanHistory.class);
        verify(historyService).save(captor.capture());
        ScanHistory saved = captor.getValue();

        assertThat(saved.scanId()).isEqualTo(response.data().scanId());
        assertThat(saved.redactedDisplayValue()).isEqualTo("https://example.com/reset-password");
        assertThat(saved.redactedDisplayValue()).doesNotContain("token=secret", "frag");
        assertThat(saved.normalized())
                .isEqualTo(new StoredNormalizedUrl(
                        "https", "example.com", "example.com", "example.com", null, "/reset-password", true,
                        true));
        assertThat(saved.score()).isEqualTo(60);
        assertThat(saved.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(saved.engineVersion()).isEqualTo("0.1.0");
        assertThat(saved.analyzedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(saved.ownerUserId()).isEqualTo(OWNER_ID);
        // Order must be preserved exactly as the analyzer produced it, not resorted.
        assertThat(saved.findings()).extracting(StoredFinding::ruleId).containsExactly("A_RULE", "Z_RULE");
        assertThat(saved.findings()).extracting(StoredFinding::points).containsExactly(40, 20);
    }

    @Test
    @DisplayName("anonymous scans return a safe result without an id or history write")
    void anonymousScansAreNotPersisted() {
        ScanHistoryService historyService = mock(ScanHistoryService.class);
        ScanService service = new ScanService(
                fixedAnalyzer(), new EngineProperties("0.1.0"), fixedClock, historyService);

        ScanResponse response = service.scan("https://example.com/reset-password?token=secret");

        assertThat(response.data().scanId()).isNull();
        assertThat(response.data().input()).isEqualTo("https://example.com/reset-password");
        verifyNoInteractions(historyService);
    }

    @Test
    @DisplayName("a malformed scan id is rejected as SCAN_NOT_FOUND without leaking the raw path value")
    void badUuidIsRejectedWithoutLeakingPathValue() {
        ScanHistoryService historyService = mock(ScanHistoryService.class);
        ScanService service =
                new ScanService(fixedAnalyzer(), new EngineProperties("0.1.0"), fixedClock, historyService);

        List<String> malformedIds = List.of(
                "not-a-uuid",
                "2ce16fb9-d52d-4310-8d45",
                "2ce16fb9-d52d-4310-8d45-a4e48f31889e-extra",
                "2ce16fb9-d52d-4310-8d45-a4e48f3188ze",
                "' OR '1'='1",
                "https://example.com/account?token=super-secret-value");

        for (String malformedId : malformedIds) {
            assertThatThrownBy(() -> service.get(malformedId))
                    .isInstanceOf(ScanNotFoundException.class)
                    .hasMessage("The requested scan was not found.")
                    .satisfies(exception -> {
                        for (Throwable current = exception; current != null; current = current.getCause()) {
                            assertThat(current.getMessage()).doesNotContain(malformedId);
                        }
                    });
        }

        // Malformed IDs must be rejected by the regex guard before ever touching the store.
        verifyNoInteractions(historyService);
    }

    private static UrlAnalyzer fixedAnalyzer() {
        return rawInput -> {
            NormalizedUrl normalizedUrl = new NormalizedUrl(
                    rawInput,
                    "https://example.com/reset-password",
                    "https",
                    "example.com",
                    "example.com",
                    DomainFeatures.fromAsciiHost("example.com"),
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

    private static UrlAnalyzer multiFindingAnalyzer() {
        return rawInput -> {
            NormalizedUrl normalizedUrl = new NormalizedUrl(
                    rawInput,
                    "https://example.com/reset-password",
                    "https",
                    "example.com",
                    "example.com",
                    DomainFeatures.fromAsciiHost("example.com"),
                    "example.com",
                    List.of(),
                    null,
                    "/reset-password",
                    true,
                    true,
                    false);
            RuleFinding first = RuleFinding.of("A_RULE", Severity.HIGH, 40, "first title", "first explanation");
            RuleFinding second = RuleFinding.of("Z_RULE", Severity.LOW, 20, "second title", "second explanation");
            return new AnalysisResult(normalizedUrl, List.of(first, second), 60, RiskLevel.HIGH);
        };
    }
}
