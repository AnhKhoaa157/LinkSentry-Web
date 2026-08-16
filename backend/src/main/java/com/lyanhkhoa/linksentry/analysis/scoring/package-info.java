/**
 * Risk scoring: combining findings into a number and a band.
 *
 * <p>Holds the {@link com.lyanhkhoa.linksentry.analysis.scoring.RiskScorer}
 * contract and its default implementation.
 *
 * <p>The band thresholds live here rather than on
 * {@link com.lyanhkhoa.linksentry.analysis.domain.RiskLevel} because they are
 * policy, not vocabulary: they will be tuned as rules are added, and a change to
 * them should touch one class with one test.
 */
package com.lyanhkhoa.linksentry.analysis.scoring;
