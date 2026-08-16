package com.lyanhkhoa.linksentry.analysis.domain;

/**
 * How much weight a single finding carries on its own.
 *
 * <p>Severity is a qualitative label used for grouping and ordering in the UI. It
 * is <em>not</em> the score: the numeric contribution is
 * {@link RuleFinding#points()}. Keeping the two separate means a rule's displayed
 * emphasis can be tuned without silently changing the arithmetic, and vice versa.
 */
public enum Severity {
    /** Contextual observation; on its own it implies nothing. */
    INFO,

    /** Weak signal. Common in legitimate URLs too. */
    LOW,

    /** Notable signal. Suspicious in combination with others. */
    MEDIUM,

    /** Strong signal, rare in legitimate URLs. */
    HIGH
}
