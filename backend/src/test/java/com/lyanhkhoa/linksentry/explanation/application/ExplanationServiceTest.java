package com.lyanhkhoa.linksentry.explanation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import com.lyanhkhoa.linksentry.common.config.AiExplanationProperties;
import com.lyanhkhoa.linksentry.common.config.HistoryProperties;
import com.lyanhkhoa.linksentry.explanation.domain.AiAdvisory;
import com.lyanhkhoa.linksentry.explanation.domain.ExplanationProvider;
import com.lyanhkhoa.linksentry.explanation.domain.ExplanationProviderException;
import com.lyanhkhoa.linksentry.explanation.domain.ExplanationResult;
import com.lyanhkhoa.linksentry.explanation.domain.KeyFinding;
import com.lyanhkhoa.linksentry.explanation.domain.ScanSummary;
import com.lyanhkhoa.linksentry.history.application.ScanHistoryService;
import com.lyanhkhoa.linksentry.history.application.ScanNotFoundException;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryRepository;
import com.lyanhkhoa.linksentry.history.domain.StoredFinding;
import com.lyanhkhoa.linksentry.history.domain.StoredNormalizedUrl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers everything {@link ExplanationService} is responsible for: reusing the
 * existing safe, owner-scoped, retention-aware scan lookup; refusing to call the
 * provider when the feature is disabled; translating every provider failure into
 * the one safe {@link ExplanationUnavailableException}; and — the core privacy
 * guarantee — that the {@link ScanSummary} handed to the provider never carries a
 * raw URL, hostname, or finding evidence, no matter what the underlying
 * {@link ScanHistory} contains.
 *
 * <p>{@link ScanHistoryRepository} is mocked and a real {@link ScanHistoryService}
 * wraps it, the same pattern as {@code ScanHistoryServiceTest}, so the owner and
 * retention filtering exercised here is the actual production lookup, not a
 * stand-in for it. {@link ExplanationProvider} is a small hand-written fake:
 * deterministic, capturing exactly what it was called with, per the task's
 * "fake provider adapter" requirement.
 */
class ExplanationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_OWNER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SCAN_ID = UUID.fromString("2ce16fb9-d52d-4310-8d45-a4e48f31889e");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("an enabled feature returns the provider's advisory for the caller's own retained scan")
    void returnsProviderAdvisoryForOwnedScan() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        when(repository.findRetained(eq(SCAN_ID), eq(OWNER_ID), any(Instant.class)))
                .thenReturn(Optional.of(sensitiveHistory()));
        FakeExplanationProvider provider = FakeExplanationProvider.returning(
                new AiAdvisory("This link shows several risk signals.", List.of("Verify before trusting it.")));
        ExplanationService service = newService(true, repository, provider);

        ExplanationResult result = service.explain(SCAN_ID.toString(), OWNER_ID);

        assertThat(result.summary()).isEqualTo("This link shows several risk signals.");
        assertThat(result.recommendedActions()).containsExactly("Verify before trusting it.");
    }

    @Test
    @DisplayName("riskLevel and keyFindings are assembled deterministically from the retained scan, not the provider")
    void riskLevelAndKeyFindingsAreDeterministic() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        when(repository.findRetained(eq(SCAN_ID), eq(OWNER_ID), any(Instant.class)))
                .thenReturn(Optional.of(sensitiveHistory()));
        FakeExplanationProvider provider = FakeExplanationProvider.returning(
                new AiAdvisory("explanation text", List.of("action")));
        ExplanationService service = newService(true, repository, provider);

        ExplanationResult result = service.explain(SCAN_ID.toString(), OWNER_ID);

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(result.keyFindings()).hasSize(1);
        KeyFinding keyFinding = result.keyFindings().get(0);
        assertThat(keyFinding.title()).isEqualTo("Hostname names a brand it is not registered to");
        assertThat(keyFinding.explanation()).isEqualTo("Generic rule explanation text.");
        assertThat(keyFinding.severity()).isEqualTo(Severity.HIGH);
        assertThat(keyFinding.points()).isEqualTo(30);
    }

    @Test
    @DisplayName("key findings are capped at 3, preserving the scan's existing order")
    void keyFindingsAreCappedAtThreeInExistingOrder() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        ScanHistory history = historyWithFindings(4);
        when(repository.findRetained(eq(SCAN_ID), eq(OWNER_ID), any(Instant.class))).thenReturn(Optional.of(history));
        FakeExplanationProvider provider =
                FakeExplanationProvider.returning(new AiAdvisory("explanation text", List.of("action")));
        ExplanationService service = newService(true, repository, provider);

        ExplanationResult result = service.explain(SCAN_ID.toString(), OWNER_ID);

        assertThat(result.keyFindings()).hasSize(3);
        assertThat(result.keyFindings())
                .extracting(KeyFinding::title)
                .containsExactly("Finding 0", "Finding 1", "Finding 2");
    }

    @Test
    @DisplayName("the summary handed to the provider carries score, risk level, engine version, and findings")
    void providerReceivesTheRequiredSafeSummary() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        when(repository.findRetained(eq(SCAN_ID), eq(OWNER_ID), any(Instant.class)))
                .thenReturn(Optional.of(sensitiveHistory()));
        FakeExplanationProvider provider =
                FakeExplanationProvider.returning(new AiAdvisory("explanation text", List.of("action")));
        ExplanationService service = newService(true, repository, provider);

        service.explain(SCAN_ID.toString(), OWNER_ID);

        ScanSummary summary = provider.lastSummary();
        assertThat(summary.score()).isEqualTo(75);
        assertThat(summary.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(summary.engineVersion()).isEqualTo("0.1.0");
        assertThat(summary.findings()).hasSize(1);
        ScanSummary.FindingSummary finding = summary.findings().get(0);
        assertThat(finding.ruleId()).isEqualTo("BRAND_DOMAIN_MISMATCH");
        assertThat(finding.severity()).isEqualTo(Severity.HIGH);
        assertThat(finding.points()).isEqualTo(30);
        assertThat(finding.title()).isEqualTo("Hostname names a brand it is not registered to");
        assertThat(finding.explanation()).isEqualTo("Generic rule explanation text.");
    }

    @Test
    @DisplayName("the summary never carries a raw URL, hostname, scan ID, or finding evidence")
    void summaryExcludesEveryForbiddenField() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        ScanHistory history = sensitiveHistory();
        when(repository.findRetained(eq(SCAN_ID), eq(OWNER_ID), any(Instant.class))).thenReturn(Optional.of(history));
        FakeExplanationProvider provider =
                FakeExplanationProvider.returning(new AiAdvisory("explanation text", List.of("action")));
        ExplanationService service = newService(true, repository, provider);

        service.explain(SCAN_ID.toString(), OWNER_ID);

        String summaryText = provider.lastSummary().toString();
        // Everything present in the underlying history but structurally absent from
        // ScanSummary: redacted display value, host, registrable domain, scan ID,
        // and the finding's evidence.
        assertThat(summaryText)
                .doesNotContain("evil-secret-token.example")
                .doesNotContain("evil-domain.xyz")
                .doesNotContain(SCAN_ID.toString())
                .doesNotContain("Vietcombank official domain(s)");
    }

    @Test
    @DisplayName("mapping a ScanHistory to a ScanSummary drops evidence and every identifying field")
    void toSummaryMappingDropsForbiddenFields() {
        ScanSummary summary = ExplanationService.toSummary(sensitiveHistory());

        assertThat(summary.findings()).hasSize(1);
        assertThat(summary.toString()).doesNotContain("evil-domain.xyz").doesNotContain("evidence");
    }

    @Test
    @DisplayName("a disabled feature is refused before the provider or the history lookup is ever touched")
    void disabledFeatureNeverCallsProviderOrHistory() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        FakeExplanationProvider provider = FakeExplanationProvider.thatMustNotBeCalled();
        ExplanationService service = newService(false, repository, provider);

        assertThatThrownBy(() -> service.explain(SCAN_ID.toString(), OWNER_ID))
                .isInstanceOf(ExplanationUnavailableException.class);

        assertThat(provider.wasCalled()).isFalse();
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("a scan owned by a different account returns the same safe not-found, never the other owner's data")
    void crossOwnerScanIsNotFound() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        when(repository.findRetained(eq(SCAN_ID), eq(OTHER_OWNER_ID), any(Instant.class)))
                .thenReturn(Optional.empty());
        FakeExplanationProvider provider = FakeExplanationProvider.thatMustNotBeCalled();
        ExplanationService service = newService(true, repository, provider);

        assertThatThrownBy(() -> service.explain(SCAN_ID.toString(), OTHER_OWNER_ID))
                .isInstanceOf(ScanNotFoundException.class);
        assertThat(provider.wasCalled()).isFalse();
    }

    @Test
    @DisplayName("a null owner (defensive: the route always requires authentication) is not found, never the provider")
    void nullOwnerIsNotFound() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        FakeExplanationProvider provider = FakeExplanationProvider.thatMustNotBeCalled();
        ExplanationService service = newService(true, repository, provider);

        assertThatThrownBy(() -> service.explain(SCAN_ID.toString(), null)).isInstanceOf(ScanNotFoundException.class);
        assertThat(provider.wasCalled()).isFalse();
    }

    @Test
    @DisplayName("a malformed scan ID is not found, the same as everywhere else it is accepted")
    void malformedScanIdIsNotFound() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        FakeExplanationProvider provider = FakeExplanationProvider.thatMustNotBeCalled();
        ExplanationService service = newService(true, repository, provider);

        assertThatThrownBy(() -> service.explain("not-a-uuid", OWNER_ID)).isInstanceOf(ScanNotFoundException.class);
        assertThat(provider.wasCalled()).isFalse();
    }

    @Test
    @DisplayName("an expired (outside retention) scan is not found")
    void expiredScanIsNotFound() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        when(repository.findRetained(eq(SCAN_ID), eq(OWNER_ID), any(Instant.class))).thenReturn(Optional.empty());
        FakeExplanationProvider provider = FakeExplanationProvider.thatMustNotBeCalled();
        ExplanationService service = newService(true, repository, provider);

        assertThatThrownBy(() -> service.explain(SCAN_ID.toString(), OWNER_ID))
                .isInstanceOf(ScanNotFoundException.class);
    }

    @Test
    @DisplayName("a provider timeout, failure, or malformed response all surface as the one safe unavailable error")
    void providerFailureBecomesTheOneSafeUnavailableError() {
        ScanHistoryRepository repository = mock(ScanHistoryRepository.class);
        when(repository.findRetained(eq(SCAN_ID), eq(OWNER_ID), any(Instant.class)))
                .thenReturn(Optional.of(sensitiveHistory()));
        FakeExplanationProvider provider = FakeExplanationProvider.thatThrows();
        ExplanationService service = newService(true, repository, provider);

        assertThatThrownBy(() -> service.explain(SCAN_ID.toString(), OWNER_ID))
                .isInstanceOf(ExplanationUnavailableException.class)
                .hasMessageNotContaining("DeepSeek")
                .hasMessageNotContaining("timed out");
    }

    private ExplanationService newService(
            boolean enabled, ScanHistoryRepository repository, ExplanationProvider provider) {
        AiExplanationProperties properties = enabled
                ? new AiExplanationProperties(true, new AiExplanationProperties.DeepSeek("test-key", "test-model"))
                : new AiExplanationProperties(false, null);
        ScanHistoryService historyService = new ScanHistoryService(repository, new HistoryProperties(30), clock);
        return new ExplanationService(properties, provider, historyService);
    }

    /** A retained scan history with {@code count} findings, in order, titled "Finding 0", "Finding 1", ... */
    private static ScanHistory historyWithFindings(int count) {
        StoredNormalizedUrl normalized = new StoredNormalizedUrl(
                "https", "example.test", "example.test", "example.test", null, "/", false, false);
        List<StoredFinding> findings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            findings.add(new StoredFinding(
                    "RULE_" + i, Severity.LOW, i, "Finding " + i, "Generic explanation " + i + ".", null));
        }
        return new ScanHistory(
                SCAN_ID, "https://example.test/", normalized, 10, RiskLevel.LOW, findings, "0.1.0", NOW, OWNER_ID);
    }

    /** A scan history deliberately packed with every field that must never reach the provider. */
    private static ScanHistory sensitiveHistory() {
        StoredNormalizedUrl normalized = new StoredNormalizedUrl(
                "https",
                "login.evil-secret-token.example",
                "login.evil-secret-token.example",
                "evil-domain.xyz",
                null,
                "/account",
                true,
                true);
        StoredFinding finding = new StoredFinding(
                "BRAND_DOMAIN_MISMATCH",
                Severity.HIGH,
                30,
                "Hostname names a brand it is not registered to",
                "Generic rule explanation text.",
                "Vietcombank official domain(s): vietcombank.com.vn");
        return new ScanHistory(
                SCAN_ID,
                "https://login.evil-secret-token.example/account",
                normalized,
                75,
                RiskLevel.CRITICAL,
                List.of(finding),
                "0.1.0",
                NOW,
                OWNER_ID);
    }

    /** Deterministic hand-written test double for {@link ExplanationProvider}. */
    private static final class FakeExplanationProvider implements ExplanationProvider {

        private final AiAdvisory result;
        private final boolean shouldThrow;
        private final boolean mustNotBeCalled;
        private final List<ScanSummary> calls = new ArrayList<>();

        private FakeExplanationProvider(AiAdvisory result, boolean shouldThrow, boolean mustNotBeCalled) {
            this.result = result;
            this.shouldThrow = shouldThrow;
            this.mustNotBeCalled = mustNotBeCalled;
        }

        static FakeExplanationProvider returning(AiAdvisory result) {
            return new FakeExplanationProvider(result, false, false);
        }

        static FakeExplanationProvider thatThrows() {
            return new FakeExplanationProvider(null, true, false);
        }

        static FakeExplanationProvider thatMustNotBeCalled() {
            return new FakeExplanationProvider(null, false, true);
        }

        @Override
        public AiAdvisory explain(ScanSummary summary) {
            if (mustNotBeCalled) {
                throw new AssertionError("ExplanationProvider must not be called in this scenario");
            }
            calls.add(summary);
            if (shouldThrow) {
                // Represents timeout, provider failure, and malformed response alike:
                // DeepSeekExplanationProvider unifies all three into this one type.
                throw new ExplanationProviderException("provider call timed out");
            }
            return result;
        }

        boolean wasCalled() {
            return !calls.isEmpty();
        }

        ScanSummary lastSummary() {
            return calls.get(calls.size() - 1);
        }
    }
}
