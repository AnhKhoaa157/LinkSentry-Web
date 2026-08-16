package com.lyanhkhoa.linksentry.analysis.rules;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import java.util.Objects;
import java.util.Optional;

/**
 * Flags links whose hostname has an unusually deep chain of subdomains.
 *
 * <p>A deceptive URL often smuggles a trusted brand name into a subdomain label
 * ({@code login.bank-example.com.evil-domain.xyz}) so it appears near the start of
 * the address, ahead of the domain that is actually registered. A deep subdomain
 * chain is where that trick lives.
 */
public final class ExcessiveSubdomainsRule implements AnalysisRule {

    /** Stable machine-readable identifier for this rule. */
    public static final String RULE_ID = "EXCESSIVE_SUBDOMAINS";

    private static final Severity SEVERITY = Severity.MEDIUM;

    private static final int POINTS = 20;

    private static final String TITLE = "Unusually deep subdomain structure";

    private static final String EXPLANATION =
            "The hostname contains more subdomain levels than expected. This is a "
                    + "technique sometimes used to make an untrusted domain look like a "
                    + "trusted brand by burying the real registered domain further along "
                    + "the address.";

    private final int maxDepth;

    /**
     * @param maxDepth deepest subdomain chain tolerated without a finding; must not be negative
     */
    public ExcessiveSubdomainsRule(int maxDepth) {
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must not be negative, was " + maxDepth);
        }
        this.maxDepth = maxDepth;
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Optional<RuleFinding> analyze(NormalizedUrl url) {
        Objects.requireNonNull(url, "url");
        if (url.subdomainDepth() <= maxDepth) {
            return Optional.empty();
        }
        return Optional.of(RuleFinding.of(RULE_ID, SEVERITY, POINTS, TITLE, EXPLANATION));
    }
}
