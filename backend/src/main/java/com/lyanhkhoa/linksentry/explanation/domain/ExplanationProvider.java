package com.lyanhkhoa.linksentry.explanation.domain;

/**
 * Port for an advisory, structured explanation of a {@link ScanSummary}.
 *
 * <p>A provider never decides score, risk level, findings, severity, points,
 * persistence, or access control — it only turns an already-computed,
 * already-safe summary into a concise advisory sentence and one or two
 * recommended actions, returned as {@link AiAdvisory}. Implementations must
 * perform no I/O beyond the one call this method represents: no retry loop, no
 * streaming, no tool use, no logging of the summary or the returned text.
 */
public interface ExplanationProvider {

    /**
     * @param summary the safe summary to explain
     * @return a short, risk-oriented advisory summary and 1-2 recommended actions
     * @throws ExplanationProviderException when the provider is unavailable, times
     *                                       out, fails, or returns an unusable response
     */
    AiAdvisory explain(ScanSummary summary);
}
