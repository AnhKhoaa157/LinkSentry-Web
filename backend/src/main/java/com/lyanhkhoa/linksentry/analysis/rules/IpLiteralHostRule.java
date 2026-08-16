package com.lyanhkhoa.linksentry.analysis.rules;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import java.util.Objects;
import java.util.Optional;

/**
 * Flags links whose host is a raw IP address rather than a domain name.
 *
 * <p>Legitimate sites are almost always reached by name; a bare IP address denies
 * a visitor the usual cues (a recognisable brand, a registrar, a certificate
 * subject) that a domain name provides, and is common in phishing and malware
 * delivery links.
 */
public final class IpLiteralHostRule implements AnalysisRule {

    /** Stable machine-readable identifier for this rule. */
    public static final String RULE_ID = "IP_LITERAL_HOST";

    /** Notable signal: no domain identity to evaluate at all. */
    private static final Severity SEVERITY = Severity.MEDIUM;

    private static final int POINTS = 15;

    private static final String TITLE = "Address uses a raw IP instead of a domain name";

    private static final String EXPLANATION =
            "This link points directly at a numeric network address instead of a "
                    + "named website. Legitimate services are almost always reached by name; "
                    + "a bare address is a common way to hide who actually operates the site.";

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Optional<RuleFinding> analyze(NormalizedUrl url) {
        Objects.requireNonNull(url, "url");
        if (!url.ipLiteral()) {
            return Optional.empty();
        }
        return Optional.of(RuleFinding.of(RULE_ID, SEVERITY, POINTS, TITLE, EXPLANATION));
    }
}
