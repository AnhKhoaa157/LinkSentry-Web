package com.lyanhkhoa.linksentry.analysis.rules;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import java.util.Objects;
import java.util.Optional;

/**
 * Flags links whose total submitted length is unusually long.
 *
 * <p>Excessive length is often used to push the meaningful part of a URL out of
 * view, or to pack in obfuscation — including in the query string, which
 * {@link NormalizedUrl#redactedDisplayValue()} deliberately strips. Measuring
 * length is therefore the one place a rule reads
 * {@link NormalizedUrl#originalInput()}: the count crosses the rule/finding
 * boundary, never the text itself. {@link RuleFinding} carries no evidence here,
 * and nothing in this class logs, returns, or otherwise renders the raw input.
 *
 * <p>The threshold is configuration, not a literal, because "unusually long" is a
 * judgement call that will be tuned over time.
 */
public final class ExcessiveUrlLengthRule implements AnalysisRule {

    /** Stable machine-readable identifier for this rule. */
    public static final String RULE_ID = "EXCESSIVE_URL_LENGTH";

    /** Weak signal on its own: some legitimate URLs (e.g. tracked marketing links) are long too. */
    private static final Severity SEVERITY = Severity.LOW;

    private static final int POINTS = 10;

    private static final String TITLE = "Link is unusually long";

    private static final String EXPLANATION =
            "This link is much longer than a typical web address. Excessive length is "
                    + "sometimes used to hide the real destination or bury suspicious detail "
                    + "where it won't be noticed.";

    private final int maxLength;

    /**
     * @param maxLength longest total submitted URL tolerated without a finding, in
     *                  characters; must be positive
     */
    public ExcessiveUrlLengthRule(int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be positive, was " + maxLength);
        }
        this.maxLength = maxLength;
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Optional<RuleFinding> analyze(NormalizedUrl url) {
        Objects.requireNonNull(url, "url");
        if (url.originalInput().length() <= maxLength) {
            return Optional.empty();
        }
        return Optional.of(RuleFinding.of(RULE_ID, SEVERITY, POINTS, TITLE, EXPLANATION));
    }
}
