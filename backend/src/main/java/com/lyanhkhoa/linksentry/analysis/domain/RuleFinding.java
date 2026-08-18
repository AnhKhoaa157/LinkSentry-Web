package com.lyanhkhoa.linksentry.analysis.domain;

import java.util.Objects;

/**
 * One explainable observation produced by one {@link AnalysisRule}.
 *
 * <p>This is the unit that makes the product explainable: the score is the sum of
 * these findings' points, and the UI explanation is these findings' text. The
 * number and the reason therefore cannot drift apart, because they come from the
 * same object.
 *
 * @param ruleId      matches the producing rule's {@link AnalysisRule#id()}
 * @param severity    qualitative weight, for grouping and ordering in the UI
 * @param points      the explicit, non-negative contribution to the risk score
 * @param title       short label, e.g. "Unusually deep subdomain structure"
 * @param explanation one or two sentences a non-expert can act on. Explain what
 *                     was observed and why it matters, not which regex matched.
 * @param evidence    optional supporting detail, or {@code null}. Must be derived
 *                     only from {@link NormalizedUrl#redactedDisplayValue()} or
 *                     from static, curated configuration (for example a brand's
 *                     display name and official domains) — never from
 *                     credentials, a raw query value, or any other unredacted
 *                     input.
 */
public record RuleFinding(
        String ruleId, Severity severity, int points, String title, String explanation, String evidence) {

    public RuleFinding {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(explanation, "explanation");
        if (ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId must not be blank");
        }
        if (points < 0) {
            // A rule may contribute nothing, but it must never reduce the risk of
            // another rule's finding: that would make the score unexplainable.
            throw new IllegalArgumentException("points must not be negative, was " + points);
        }
    }

    /** Creates a finding without supporting evidence. */
    public static RuleFinding of(String ruleId, Severity severity, int points, String title, String explanation) {
        return new RuleFinding(ruleId, severity, points, title, explanation, null);
    }

    /** Creates a finding with already-redacted supporting evidence. */
    public static RuleFinding of(
            String ruleId, Severity severity, int points, String title, String explanation, String evidence) {
        return new RuleFinding(ruleId, severity, points, title, explanation, evidence);
    }
}
