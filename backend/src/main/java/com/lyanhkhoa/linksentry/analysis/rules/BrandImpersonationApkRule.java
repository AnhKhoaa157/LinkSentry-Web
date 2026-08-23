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

/**
 * Combines a curated brand match with a literal Android package download suffix.
 *
 * <p>The approved production policy contributes {@link Severity#MEDIUM} severity
 * and 20 points when the composite signal fires.
 *
 * <p>The brand side reuses the existing exact-token policy and the existing
 * bounded lookalike implementation. The URL path is consulted only for its
 * final, literal, case-insensitive {@code .apk} suffix; no percent decoding is
 * performed. All evidence is static registry data plus a generic signal label.
 */
public final class BrandImpersonationApkRule implements AnalysisRule {

    /** Stable machine-readable identifier for this composite rule. */
    public static final String RULE_ID = "BRAND_IMPERSONATION_APK";

    private static final String TITLE = "Brand impersonation paired with an Android package";

    private static final String EXPLANATION =
            "This address combines a known brand name with a download of an Android application package. "
                    + "Use the organisation's official channel to verify before installing anything. "
                    + "This signal does not confirm that the link is malicious.";

    private static final String SIGNAL_DESCRIPTION = "Android package download";
    private static final Severity SEVERITY = Severity.MEDIUM;
    private static final int POINTS = 20;

    private final List<BrandMatcher> brandMatchers;

    /** Creates the approved composite rule for the curated registry. */
    public BrandImpersonationApkRule(BrandRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        this.brandMatchers = registry.brands().stream()
                .map(brand -> new BrandMatcher(
                        brand,
                        new BrandLookalikeRule(new BrandRegistry(List.of(brand)))))
                .toList();
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Optional<RuleFinding> analyze(NormalizedUrl url) {
        Objects.requireNonNull(url, "url");

        if (url.ipLiteral() || url.registrableDomain() == null || !hasLiteralApkSuffix(url.path())) {
            return Optional.empty();
        }

        Optional<Brand> matchedBrand = findMatchedBrand(url);
        if (matchedBrand.isEmpty()) {
            return Optional.empty();
        }

        Brand brand = matchedBrand.get();
        String evidence = brand.displayName() + " official domain(s): "
                + String.join(", ", brand.officialDomains()) + "; signal: " + SIGNAL_DESCRIPTION;
        return Optional.of(RuleFinding.of(RULE_ID, SEVERITY, POINTS, TITLE, EXPLANATION, evidence));
    }

    private Optional<Brand> findMatchedBrand(NormalizedUrl url) {
        Set<String> hostTokens = url.domainFeatures().hostTokens();
        for (BrandMatcher matcher : brandMatchers) {
            Brand brand = matcher.brand();
            boolean exactMatch = brand.tokens().stream().anyMatch(hostTokens::contains);
            boolean lookalikeMatch = !exactMatch && matcher.lookalikeRule().analyze(url).isPresent();
            if (!exactMatch && !lookalikeMatch) {
                continue;
            }

            // Match selection follows the ordered existing brand rules: the first
            // matching brand owns the decision, including the official-domain guard.
            if (brand.officialDomains().contains(url.registrableDomain())) {
                return Optional.empty();
            }
            return Optional.of(brand);
        }
        return Optional.empty();
    }

    private static boolean hasLiteralApkSuffix(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        int finalSlash = path.lastIndexOf('/');
        if (finalSlash == path.length() - 1) {
            return false;
        }
        String finalSegment = path.substring(finalSlash + 1);
        return finalSegment.toLowerCase(Locale.ROOT).endsWith(".apk");
    }

    private record BrandMatcher(Brand brand, BrandLookalikeRule lookalikeRule) {}
}
