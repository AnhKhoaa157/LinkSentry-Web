package com.lyanhkhoa.linksentry.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.analysis.normalization.DefaultUrlNormalizer;
import com.lyanhkhoa.linksentry.analysis.scoring.DefaultRiskScorer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultUrlAnalyzerTest {

    private final DefaultUrlNormalizer normalizer = new DefaultUrlNormalizer();
    private final DefaultRiskScorer scorer = new DefaultRiskScorer();

    @Test
    @DisplayName("aggregates every rule's finding into the result and scores their sum")
    void aggregatesFindingsAndScore() {
        DefaultUrlAnalyzer analyzer = new DefaultUrlAnalyzer(
                normalizer, List.of(fixedRule("RULE_A", 10), fixedRule("RULE_B", 20)), scorer);

        AnalysisResult result = analyzer.analyze("https://example.com/");

        assertThat(result.findings()).hasSize(2);
        assertThat(result.score()).isEqualTo(30);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MODERATE);
    }

    @Test
    @DisplayName("a rule producing no finding contributes nothing")
    void quietRuleContributesNothing() {
        DefaultUrlAnalyzer analyzer =
                new DefaultUrlAnalyzer(normalizer, List.of(fixedRule("RULE_A", 10), silentRule("RULE_B")), scorer);

        AnalysisResult result = analyzer.analyze("https://example.com/");

        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).ruleId()).isEqualTo("RULE_A");
    }

    @Test
    @DisplayName("findings are ordered by descending points, then ascending rule id on ties")
    void findingsAreOrderedDeterministically() {
        DefaultUrlAnalyzer analyzer = new DefaultUrlAnalyzer(
                normalizer,
                List.of(fixedRule("ZEBRA", 10), fixedRule("ALPHA", 30), fixedRule("BETA", 10)),
                scorer);

        AnalysisResult result = analyzer.analyze("https://example.com/");

        assertThat(result.findings()).extracting(RuleFinding::ruleId).containsExactly("ALPHA", "BETA", "ZEBRA");
    }

    @Test
    @DisplayName("ordering is independent of the order rules were injected in")
    void orderingIsIndependentOfInjectionOrder() {
        DefaultUrlAnalyzer analyzer = new DefaultUrlAnalyzer(
                normalizer,
                List.of(fixedRule("BETA", 10), fixedRule("ZEBRA", 10), fixedRule("ALPHA", 30)),
                scorer);

        AnalysisResult result = analyzer.analyze("https://example.com/");

        assertThat(result.findings()).extracting(RuleFinding::ruleId).containsExactly("ALPHA", "BETA", "ZEBRA");
    }

    @Test
    @DisplayName("a rule that throws fails the whole scan rather than producing a partial result")
    void failingRuleFailsTheWholeScan() {
        DefaultUrlAnalyzer analyzer = new DefaultUrlAnalyzer(
                normalizer, List.of(fixedRule("GOOD", 10), throwingRule("BROKEN")), scorer);

        org.assertj.core.api.Assertions.assertThatExceptionOfType(RuleExecutionException.class)
                .isThrownBy(() -> analyzer.analyze("https://example.com/"))
                .withMessageContaining("BROKEN")
                .withMessageContaining("IllegalStateException")
                // The original message can carry fragments derived from the URL and must never surface.
                .withMessageNotContaining("simulated rule failure");
    }

    @Test
    @DisplayName("a failing rule still runs even when it would have been the only contributor")
    void failingRuleFailsEvenWhenAlone() {
        DefaultUrlAnalyzer analyzer = new DefaultUrlAnalyzer(normalizer, List.of(throwingRule("BROKEN")), scorer);

        org.assertj.core.api.Assertions.assertThatExceptionOfType(RuleExecutionException.class)
                .isThrownBy(() -> analyzer.analyze("https://example.com/"));
    }

    @Test
    @DisplayName("an invalid URL still fails fast with InvalidUrlException")
    void invalidUrlPropagates() {
        DefaultUrlAnalyzer analyzer = new DefaultUrlAnalyzer(normalizer, List.of(), scorer);

        org.assertj.core.api.Assertions.assertThatExceptionOfType(InvalidUrlException.class)
                .isThrownBy(() -> analyzer.analyze("not a url"));
    }

    private static AnalysisRule fixedRule(String ruleId, int points) {
        return new AnalysisRule() {
            @Override
            public String id() {
                return ruleId;
            }

            @Override
            public Optional<RuleFinding> analyze(NormalizedUrl url) {
                return Optional.of(RuleFinding.of(ruleId, Severity.MEDIUM, points, "title", "explanation"));
            }
        };
    }

    private static AnalysisRule silentRule(String ruleId) {
        return new AnalysisRule() {
            @Override
            public String id() {
                return ruleId;
            }

            @Override
            public Optional<RuleFinding> analyze(NormalizedUrl url) {
                return Optional.empty();
            }
        };
    }

    private static AnalysisRule throwingRule(String ruleId) {
        return new AnalysisRule() {
            @Override
            public String id() {
                return ruleId;
            }

            @Override
            public Optional<RuleFinding> analyze(NormalizedUrl url) {
                throw new IllegalStateException("simulated rule failure");
            }
        };
    }
}
