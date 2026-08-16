package com.lyanhkhoa.linksentry.analysis.domain;

import java.util.List;
import java.util.Objects;

/**
 * The complete outcome of analysing one URL.
 *
 * <p>Carries no scan id and no timestamp on purpose. Identity and time are the
 * application layer's concern; leaving them out keeps
 * {@link UrlAnalyzer#analyze(String)} a pure function of its input, which is what
 * makes it exhaustively testable without a fixed {@code Clock}. The scan layer
 * stamps both when it builds the HTTP response.
 *
 * @param normalizedUrl what was actually analysed
 * @param findings      every finding produced, in a deterministic order
 * @param score         total risk score, clamped to {@code 0..100}
 * @param riskLevel     band {@code score} falls into
 */
public record AnalysisResult(NormalizedUrl normalizedUrl, List<RuleFinding> findings, int score, RiskLevel riskLevel) {

    /** Lowest possible score. Note that it is not a claim of safety. */
    public static final int MIN_SCORE = 0;

    /** Highest possible score. */
    public static final int MAX_SCORE = 100;

    public AnalysisResult {
        Objects.requireNonNull(normalizedUrl, "normalizedUrl");
        Objects.requireNonNull(riskLevel, "riskLevel");
        findings = findings == null ? List.of() : List.copyOf(findings);
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException(
                    "score must be within " + MIN_SCORE + ".." + MAX_SCORE + ", was " + score);
        }
    }

    /**
     * Whether any rule produced a finding.
     *
     * <p>{@code true} from this method means "nothing was detected". It does
     * <strong>not</strong> mean the link is safe, and no UI copy may present it
     * that way.
     */
    public boolean hasNoFindings() {
        return findings.isEmpty();
    }
}
