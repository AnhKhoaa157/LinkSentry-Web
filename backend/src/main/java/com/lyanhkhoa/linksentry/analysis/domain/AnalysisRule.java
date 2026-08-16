package com.lyanhkhoa.linksentry.analysis.domain;

import java.util.Optional;

/**
 * One independent, explainable check against a {@link NormalizedUrl}.
 *
 * <p>Implementations belong in {@code analysis.rules} — one class per rule, one
 * test class per rule (Exercise 4).
 *
 * <p>Invariants every implementation must uphold:
 *
 * <ul>
 *   <li><strong>Pure.</strong> No network access, no file access, no clock, no
 *       randomness. Same input, same output, forever.
 *   <li><strong>Stateless.</strong> A single instance is shared across concurrent
 *       requests, so it must hold no mutable state.
 *   <li><strong>Self-contained.</strong> A rule reports what it observed and never
 *       mutates a shared score; combining points is
 *       {@link com.lyanhkhoa.linksentry.analysis.scoring.RiskScorer}'s job.
 *   <li><strong>Quiet by default.</strong> Returning empty is the common case and
 *       must be cheap.
 * </ul>
 */
public interface AnalysisRule {

    /**
     * Stable machine-readable identifier, e.g. {@code EXCESSIVE_SUBDOMAINS}.
     *
     * <p>This value reaches API clients and, eventually, stored history rows.
     * Treat it as part of the public contract: renaming one is a breaking change.
     *
     * @return a non-blank identifier, unique across all rules
     */
    String id();

    /**
     * Inspects a URL and reports at most one finding.
     *
     * @param url the URL to inspect; never {@code null}
     * @return a finding whose {@code ruleId} equals {@link #id()}, or
     *         {@link Optional#empty()} when this rule observed nothing
     */
    Optional<RuleFinding> analyze(NormalizedUrl url);
}
