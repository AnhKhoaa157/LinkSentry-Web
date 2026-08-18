package com.lyanhkhoa.linksentry.analysis.rules;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Flags a hostname that names a known brand but is not registered to that brand.
 *
 * <p>Example: {@code login.vietcombank.com.vn.evil-domain.xyz} contains the token
 * {@code vietcombank} in its subdomain chain, but its registrable domain is
 * {@code evil-domain.xyz} — not one of Vietcombank's configured official domains.
 * That mismatch is the signal; the token appearing in a subdomain of an official
 * domain (or as the registrable domain itself) is not.
 *
 * <p>Only {@link NormalizedUrl#asciiHost()} is inspected, tokenized on {@code .}
 * and {@code -}, and compared for exact matches against each {@link Brand}'s
 * curated tokens. The path, query, fragment, credentials, DNS, page content,
 * redirects and network data are never consulted — see
 * {@code docs/SECURITY_BOUNDARY.md}.
 *
 * <p>When several configured brands match, exactly one is chosen — the first, in
 * {@link BrandRegistry} order, whose token matched — and only that brand's domains
 * are compared. This rule never contributes more than one finding.
 */
public final class BrandDomainMismatchRule implements AnalysisRule {

    /** Stable machine-readable identifier for this rule. */
    public static final String RULE_ID = "BRAND_DOMAIN_MISMATCH";

    /** Strong signal: a named brand token paired with a domain that brand does not control. */
    private static final Severity SEVERITY = Severity.HIGH;

    private static final int POINTS = 30;

    private static final String TITLE = "Hostname names a brand it is not registered to";

    private static final String EXPLANATION =
            "This address contains the name of a known brand, but the domain actually "
                    + "registered for this link does not belong to that brand. This is a common "
                    + "technique for impersonating a trusted organisation's login or account pages. "
                    + "This does not prove the link is malicious — the brand name may be an "
                    + "unrelated coincidence — but it is worth verifying before entering any "
                    + "credentials.";

    private final BrandRegistry registry;

    public BrandDomainMismatchRule(BrandRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Optional<RuleFinding> analyze(NormalizedUrl url) {
        Objects.requireNonNull(url, "url");

        String registrableDomain = url.registrableDomain();
        if (registrableDomain == null) {
            return Optional.empty();
        }

        Set<String> hostTokens = tokenize(url.asciiHost());

        for (Brand brand : registry.brands()) {
            boolean tokenMatched = brand.tokens().stream().anyMatch(hostTokens::contains);
            if (!tokenMatched) {
                continue;
            }
            if (brand.officialDomains().contains(registrableDomain)) {
                return Optional.empty();
            }
            String evidence = brand.displayName() + " official domain(s): "
                    + String.join(", ", brand.officialDomains());
            return Optional.of(RuleFinding.of(RULE_ID, SEVERITY, POINTS, TITLE, EXPLANATION, evidence));
        }

        return Optional.empty();
    }

    private static Set<String> tokenize(String asciiHost) {
        Set<String> tokens = new HashSet<>();
        for (String label : asciiHost.split("\\.", -1)) {
            for (String part : label.split("-", -1)) {
                if (!part.isEmpty()) {
                    tokens.add(part);
                }
            }
        }
        return tokens;
    }
}
