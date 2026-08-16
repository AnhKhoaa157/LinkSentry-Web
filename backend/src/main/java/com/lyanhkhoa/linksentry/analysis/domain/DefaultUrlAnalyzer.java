package com.lyanhkhoa.linksentry.analysis.domain;

import com.lyanhkhoa.linksentry.analysis.normalization.UrlNormalizer;
import com.lyanhkhoa.linksentry.analysis.scoring.RiskScorer;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Runs every injected {@link AnalysisRule} against a normalized URL and combines
 * the result with a {@link RiskScorer}.
 *
 * <p>Findings are sorted deterministically — descending {@link RuleFinding#points()},
 * then ascending {@link RuleFinding#ruleId()} to break ties — so the same input
 * always produces the same response, regardless of the order rules were injected in.
 *
 * <p>A single rule throwing fails the whole scan with a {@link RuleExecutionException},
 * rather than being skipped. A score that silently omits one rule's contribution is
 * indistinguishable from a score that rule had nothing to report — the client (and
 * the operator investigating the failure) cannot tell a degraded result from a
 * genuinely clean one, which is worse than a request that visibly fails.
 */
public final class DefaultUrlAnalyzer implements UrlAnalyzer {

    private static final Comparator<RuleFinding> FINDING_ORDER =
            Comparator.comparingInt(RuleFinding::points).reversed().thenComparing(RuleFinding::ruleId);

    private final UrlNormalizer normalizer;
    private final List<AnalysisRule> rules;
    private final RiskScorer scorer;

    public DefaultUrlAnalyzer(UrlNormalizer normalizer, List<AnalysisRule> rules, RiskScorer scorer) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        this.scorer = Objects.requireNonNull(scorer, "scorer");
    }

    @Override
    public AnalysisResult analyze(String rawInput) {
        NormalizedUrl normalizedUrl = normalizer.normalize(rawInput);

        List<RuleFinding> findings = rules.stream()
                .map(rule -> executeRule(rule, normalizedUrl))
                .flatMap(Optional::stream)
                .sorted(FINDING_ORDER)
                .toList();

        int score = scorer.score(findings);
        RiskLevel riskLevel = scorer.levelOf(score);

        return new AnalysisResult(normalizedUrl, findings, score, riskLevel);
    }

    private Optional<RuleFinding> executeRule(AnalysisRule rule, NormalizedUrl normalizedUrl) {
        try {
            return rule.analyze(normalizedUrl);
        } catch (RuntimeException e) {
            // Neither e's message nor e itself is attached: both could quote
            // fragments of the analysed URL. See RuleExecutionException's javadoc.
            throw new RuleExecutionException(rule.id(), e.getClass().getSimpleName());
        }
    }
}
