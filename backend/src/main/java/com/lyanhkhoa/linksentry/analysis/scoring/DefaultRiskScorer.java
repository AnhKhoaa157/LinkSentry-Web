package com.lyanhkhoa.linksentry.analysis.scoring;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisResult;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import java.util.List;
import java.util.Objects;

/**
 * Sums finding points and maps the total onto four score bands.
 *
 * <p>The bands are a deliberate policy choice, documented here rather than buried
 * in a comparison chain, so they can be reasoned about and revised as one
 * decision:
 *
 * <ul>
 *   <li>{@code 0..9}   → {@link RiskLevel#LOW} — no findings, or only the weakest
 *       single signal (e.g. missing HTTPS alone).
 *   <li>{@code 10..39} → {@link RiskLevel#MODERATE} — one clear signal, or a couple
 *       of weak ones; worth a second look.
 *   <li>{@code 40..69} → {@link RiskLevel#HIGH} — multiple signals, or one strong
 *       one, combining into a pattern unlikely by chance.
 *   <li>{@code 70..100} → {@link RiskLevel#CRITICAL} — several strong signals
 *       together, consistent with a deliberately deceptive URL.
 * </ul>
 */
public final class DefaultRiskScorer implements RiskScorer {

    /** Upper bound (inclusive) of {@link RiskLevel#LOW}. */
    static final int LOW_MAX = 9;

    /** Upper bound (inclusive) of {@link RiskLevel#MODERATE}. */
    static final int MODERATE_MAX = 39;

    /** Upper bound (inclusive) of {@link RiskLevel#HIGH}. */
    static final int HIGH_MAX = 69;

    @Override
    public int score(List<RuleFinding> findings) {
        Objects.requireNonNull(findings, "findings");
        long total = findings.stream().mapToLong(RuleFinding::points).sum();
        if (total >= AnalysisResult.MAX_SCORE) {
            return AnalysisResult.MAX_SCORE;
        }
        return (int) Math.max(total, AnalysisResult.MIN_SCORE);
    }

    @Override
    public RiskLevel levelOf(int score) {
        if (score < AnalysisResult.MIN_SCORE || score > AnalysisResult.MAX_SCORE) {
            throw new IllegalArgumentException(
                    "score must be within " + AnalysisResult.MIN_SCORE + ".." + AnalysisResult.MAX_SCORE
                            + ", was " + score);
        }
        if (score <= LOW_MAX) {
            return RiskLevel.LOW;
        }
        if (score <= MODERATE_MAX) {
            return RiskLevel.MODERATE;
        }
        if (score <= HIGH_MAX) {
            return RiskLevel.HIGH;
        }
        return RiskLevel.CRITICAL;
    }
}
