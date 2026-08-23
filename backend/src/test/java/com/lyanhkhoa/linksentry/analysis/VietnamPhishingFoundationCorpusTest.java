package com.lyanhkhoa.linksentry.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisResult;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Synthetic Vietnamese impersonation/APK scenarios that lock the approved
 * {@code BRAND_IMPERSONATION_APK} contract.
 *
 * <p>The corpus deliberately uses only strings; {@link AnalyzerFixture} wires a
 * framework-free analyzer and never fetches, resolves, or renders any URL. The
 * final column records the approved composite-rule expectation and is asserted
 * alongside each current deterministic result.
 */
class VietnamPhishingFoundationCorpusTest {

    private static final String CORPUS_RESOURCE = "/vietnam-phishing-foundation-corpus.csv";

    private final UrlAnalyzer analyzer = AnalyzerFixture.productionAnalyzer();

    @ParameterizedTest(name = "[{index}] {0} ({1})")
    @CsvFileSource(resources = CORPUS_RESOURCE, numLinesToSkip = 1, delimiterString = "|", encoding = "UTF-8")
    @DisplayName("Vietnam phishing foundation corpus preserves the approved static contract")
    void corpusCase(
            String id,
            String category,
            String url,
            String expectedRuleIdsRaw,
            String expectedScoreRaw,
            String expectedRiskLevelRaw,
            String secretSentinelsRaw,
            String milestoneThreeCompositeExpectation) {

        String caseLabel = id + " (" + category + ")";
        AnalysisResult result = analyzer.analyze(url);

        List<String> actualRuleIds = result.findings().stream().map(RuleFinding::ruleId).toList();
        assertThat(actualRuleIds)
                .as(caseLabel + " current rule ids, in order")
                .containsExactlyElementsOf(splitOrEmpty(expectedRuleIdsRaw));
        assertThat(result.score()).as(caseLabel + " current score").isEqualTo(Integer.parseInt(expectedScoreRaw));
        assertThat(result.riskLevel())
                .as(caseLabel + " current risk level")
                .isEqualTo(RiskLevel.valueOf(expectedRiskLevelRaw));
        assertThat(milestoneThreeCompositeExpectation)
                .as(caseLabel + " must declare the approved composite-rule contract")
                .isIn("FIRE", "NO_FINDING");
        if ("FIRE".equals(milestoneThreeCompositeExpectation)) {
            assertThat(actualRuleIds).as(caseLabel + " composite rule must fire").contains("BRAND_IMPERSONATION_APK");
        } else {
            assertThat(actualRuleIds)
                    .as(caseLabel + " composite rule must not fire")
                    .doesNotContain("BRAND_IMPERSONATION_APK");
        }

        for (String sentinel : splitOrEmpty(secretSentinelsRaw)) {
            assertThat(result.normalizedUrl().redactedDisplayValue())
                    .as(caseLabel + " redacted display value")
                    .doesNotContain(sentinel);
            for (RuleFinding finding : result.findings()) {
                assertThat(finding.title()).as(caseLabel + " finding title").doesNotContain(sentinel);
                assertThat(finding.explanation()).as(caseLabel + " finding explanation").doesNotContain(sentinel);
                if (finding.evidence() != null) {
                    assertThat(finding.evidence()).as(caseLabel + " finding evidence").doesNotContain(sentinel);
                }
            }
        }
    }

    private static List<String> splitOrEmpty(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(";")).map(String::trim).filter(part -> !part.isEmpty()).toList();
    }
}
