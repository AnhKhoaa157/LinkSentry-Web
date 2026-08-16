package com.lyanhkhoa.linksentry.analysis.rules;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Flags links whose registrable domain belongs to a known URL-shortening service.
 *
 * <p>A shortener is not inherently malicious, but it hides the true destination
 * from the visitor until after the link is followed, which is exactly the property
 * phishing campaigns exploit. The domain list is configuration, since shortener
 * services come and go and the list would otherwise go stale.
 */
public final class KnownUrlShortenerRule implements AnalysisRule {

    /** Stable machine-readable identifier for this rule. */
    public static final String RULE_ID = "KNOWN_URL_SHORTENER";

    private static final Severity SEVERITY = Severity.LOW;

    private static final int POINTS = 10;

    private static final String TITLE = "Link uses a URL-shortening service";

    private static final String EXPLANATION =
            "This address belongs to a link-shortening service, which hides the real "
                    + "destination until the link is followed. Shorteners are widely used "
                    + "legitimately, but the same property makes them a common tool for "
                    + "disguising a malicious destination.";

    private final Set<String> shortenerDomains;

    /**
     * @param shortenerDomains registrable domains of known shortening services, matched
     *                         case-insensitively; must be non-empty
     */
    public KnownUrlShortenerRule(List<String> shortenerDomains) {
        Objects.requireNonNull(shortenerDomains, "shortenerDomains");
        if (shortenerDomains.isEmpty()) {
            throw new IllegalArgumentException("shortenerDomains must not be empty");
        }
        this.shortenerDomains = shortenerDomains.stream()
                .map(d -> d.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Optional<RuleFinding> analyze(NormalizedUrl url) {
        Objects.requireNonNull(url, "url");
        String registrableDomain = url.registrableDomain();
        if (registrableDomain == null || !shortenerDomains.contains(registrableDomain.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        return Optional.of(RuleFinding.of(RULE_ID, SEVERITY, POINTS, TITLE, EXPLANATION));
    }
}
