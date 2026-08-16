package com.lyanhkhoa.linksentry.analysis.rules;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import java.util.Objects;
import java.util.Optional;

/**
 * Flags links that use plain HTTP instead of HTTPS.
 *
 * <p>Missing encryption is common on legitimate, low-stakes pages (a static
 * blog, an old but harmless site) and is not by itself evidence of malice.
 * It is therefore a moderate, explainable signal rather than a strong one:
 * on its own it should nudge the score, not dominate it.
 */
public final class MissingHttpsRule implements AnalysisRule {

    /** Stable machine-readable identifier for this rule. */
    public static final String RULE_ID = "MISSING_HTTPS";

    /** Weak signal: unencrypted transport alone doesn't imply malicious intent. */
    private static final Severity SEVERITY = Severity.LOW;

    /** Small, explainable contribution — keeps this from dominating the score alone. */
    private static final int POINTS = 5;

    private static final String HTTP_SCHEME = "http";

    private static final String TITLE = "Connection is not encrypted";

    private static final String EXPLANATION =
            "This link uses HTTP instead of HTTPS, so data sent to or from the site "
                    + "travels without encryption. Anyone on the same network could read or "
                    + "tamper with it, including passwords or other sensitive information.";

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Optional<RuleFinding> analyze(NormalizedUrl url) {
        Objects.requireNonNull(url, "url");
        if (!HTTP_SCHEME.equals(url.scheme())) {
            return Optional.empty();
        }
        return Optional.of(RuleFinding.of(RULE_ID, SEVERITY, POINTS, TITLE, EXPLANATION));
    }
}
