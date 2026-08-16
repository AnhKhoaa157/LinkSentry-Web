package com.lyanhkhoa.linksentry.analysis.domain;

/**
 * Orchestrates the whole analysis pipeline. The only entry point the application
 * layer should depend on.
 *
 * <p><strong>Not implemented.</strong> Build it in Exercise 6, on top of a
 * {@link com.lyanhkhoa.linksentry.analysis.normalization.UrlNormalizer}, an
 * ordered collection of {@link AnalysisRule}s, and a
 * {@link com.lyanhkhoa.linksentry.analysis.scoring.RiskScorer}:
 *
 * <pre>
 * raw string -&gt; UrlNormalizer -&gt; NormalizedUrl
 *            -&gt; every AnalysisRule  -&gt; List&lt;RuleFinding&gt;
 *            -&gt; RiskScorer          -&gt; score + RiskLevel
 *            -&gt; AnalysisResult
 * </pre>
 *
 * <p>Invariants an implementation must uphold:
 *
 * <ul>
 *   <li><strong>Deterministic.</strong> Including the order of
 *       {@link AnalysisResult#findings()} — unstable ordering makes responses,
 *       snapshots and tests churn for no reason.
 *   <li><strong>Rules are injected, not hard-coded.</strong> Adding a rule must not
 *       require editing the analyzer.
 *   <li><strong>No network access.</strong> Ever. See
 *       {@code docs/SECURITY_BOUNDARY.md}.
 * </ul>
 */
public interface UrlAnalyzer {

    /**
     * Analyses a raw submitted URL.
     *
     * @param rawInput the raw string as submitted; must not be {@code null}
     * @return the complete, explainable result
     * @throws InvalidUrlException when {@code rawInput} is not an analysable
     *                             {@code http} or {@code https} URL
     */
    AnalysisResult analyze(String rawInput);
}
