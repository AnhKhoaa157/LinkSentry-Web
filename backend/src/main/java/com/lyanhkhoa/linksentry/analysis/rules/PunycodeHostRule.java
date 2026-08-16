package com.lyanhkhoa.linksentry.analysis.rules;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Flags links whose host contains a Punycode-encoded ({@code xn--}) label.
 *
 * <p>Punycode lets a hostname contain non-ASCII characters, which enables
 * homograph attacks where a domain that looks identical to a trusted brand (using
 * lookalike letters from another script) is actually a different domain entirely.
 * A Punycode label is not proof of an attack — legitimate internationalised domains
 * exist — but it is exactly the mechanism that attack relies on, so it is worth
 * surfacing.
 */
public final class PunycodeHostRule implements AnalysisRule {

    /** Stable machine-readable identifier for this rule. */
    public static final String RULE_ID = "PUNYCODE_HOST";

    private static final Severity SEVERITY = Severity.MEDIUM;

    private static final int POINTS = 15;

    private static final String TITLE = "Domain name uses internationalised (Punycode) encoding";

    private static final String EXPLANATION =
            "Part of this address is encoded to represent characters from a non-Latin "
                    + "alphabet. This encoding is what makes it possible for a domain to visually "
                    + "mimic a trusted brand using lookalike letters, so it deserves a closer look "
                    + "even though it is sometimes used legitimately.";

    private static final String PUNYCODE_PREFIX = "xn--";

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Optional<RuleFinding> analyze(NormalizedUrl url) {
        Objects.requireNonNull(url, "url");
        if (url.ipLiteral()) {
            return Optional.empty();
        }
        boolean hasPunycodeLabel = Arrays.stream(url.asciiHost().split("\\.", -1))
                .anyMatch(label -> label.toLowerCase(Locale.ROOT).startsWith(PUNYCODE_PREFIX));
        if (!hasPunycodeLabel) {
            return Optional.empty();
        }
        return Optional.of(RuleFinding.of(RULE_ID, SEVERITY, POINTS, TITLE, EXPLANATION));
    }
}
