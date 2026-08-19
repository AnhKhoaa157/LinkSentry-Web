package com.lyanhkhoa.linksentry.explanation.domain;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import java.util.List;
import java.util.Objects;

/**
 * Everything an {@link ExplanationProvider} is allowed to see about one scan.
 *
 * <p>This type is the entire privacy boundary for the AI integration: it has no
 * field for a raw URL, a redacted display value, a hostname, a path, a port, a
 * query, a fragment, a credential, a remote address, a bearer token, an email, a
 * scan ID, or a trace ID, so leaking any of those through the provider prompt is
 * a compile error, not a review item. Finding evidence is excluded too — only
 * {@code ruleId}, {@code severity}, {@code points}, {@code title}, and the
 * existing generic {@code explanation} cross this boundary. See
 * {@code docs/adr/0005-anthropic-scan-explanation-integration.md}.
 *
 * @param score         total risk score, {@code 0..100}
 * @param riskLevel     band {@code score} falls into
 * @param engineVersion analysis engine version that produced the scan
 * @param findings      every finding, in the scan's existing deterministic order
 */
public record ScanSummary(int score, RiskLevel riskLevel, String engineVersion, List<FindingSummary> findings) {

    public ScanSummary {
        Objects.requireNonNull(riskLevel, "riskLevel");
        Objects.requireNonNull(engineVersion, "engineVersion");
        Objects.requireNonNull(findings, "findings");
        findings = List.copyOf(findings);
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be within 0..100");
        }
    }

    /**
     * @param ruleId      stable machine-readable identifier of the rule that fired
     * @param severity    qualitative weight
     * @param points      explicit, non-negative contribution to the score
     * @param title       short, non-technical label
     * @param explanation the rule's own existing generic explanation
     */
    public record FindingSummary(String ruleId, Severity severity, int points, String title, String explanation) {

        public FindingSummary {
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(explanation, "explanation");
            if (points < 0) {
                throw new IllegalArgumentException("points must not be negative");
            }
        }
    }
}
