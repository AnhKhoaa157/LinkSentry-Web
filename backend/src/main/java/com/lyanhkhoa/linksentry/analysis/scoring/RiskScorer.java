package com.lyanhkhoa.linksentry.analysis.scoring;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import java.util.List;

/**
 * Turns a set of findings into a transparent score and a {@link RiskLevel}.
 *
 * <p>The default implementation is Exercise 5, including the documented choice
 * of band thresholds. Keep the policy explicit rather than hiding it in a
 * comparison chain.
 *
 * <p>Invariants an implementation must uphold:
 *
 * <ul>
 *   <li><strong>Explicit arithmetic.</strong> The score derives from the findings'
 *       {@link RuleFinding#points()} and from nothing else. No hidden adjustments,
 *       no model, no heuristics the user cannot be shown.
 *   <li><strong>Clamped.</strong> Always within
 *       {@code AnalysisResult.MIN_SCORE..MAX_SCORE}.
 *   <li><strong>Deterministic and order-independent.</strong> The same findings in a
 *       different order must produce the same score.
 *   <li><strong>No implied safety.</strong> An empty finding list means nothing was
 *       detected, which is not the same as safe.
 * </ul>
 */
public interface RiskScorer {

    /**
     * Combines findings into a single clamped score.
     *
     * @param findings the findings produced for one URL; may be empty
     * @return a score within {@code 0..100}
     */
    int score(List<RuleFinding> findings);

    /**
     * Maps a score to its qualitative band.
     *
     * <p>Kept separate from {@link #score(List)} so the thresholds can be tested
     * directly at their boundaries, without constructing findings that happen to
     * add up to the right number.
     *
     * @param score a score within {@code 0..100}
     * @return the band the score falls into
     */
    RiskLevel levelOf(int score);
}
