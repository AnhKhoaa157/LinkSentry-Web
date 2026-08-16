package com.lyanhkhoa.linksentry.analysis.domain;

/**
 * Qualitative band a numeric risk score maps onto.
 *
 * <p>The band boundaries are deliberately <strong>not</strong> defined here. They
 * are a scoring policy, they will be tuned, and they belong to the
 * {@link com.lyanhkhoa.linksentry.analysis.scoring.RiskScorer} implementation
 * where they can be documented and tested as one decision (Exercise 5).
 *
 * <p>Note the absence of a {@code SAFE} value. Static analysis cannot establish
 * that a URL is safe, so the model offers no way to say it. {@link #LOW} means
 * "no strong signals were detected", which is a different claim.
 */
public enum RiskLevel {
    /** No strong lexical risk signals detected. Not a statement of safety. */
    LOW,

    /** Some signals present; worth a second look before trusting the link. */
    MODERATE,

    /** Multiple or significant signals present. */
    HIGH,

    /** Signals strongly consistent with a deliberately deceptive URL. */
    CRITICAL
}
