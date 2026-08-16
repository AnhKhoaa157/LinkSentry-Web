package com.lyanhkhoa.linksentry.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tunable thresholds and word lists for {@code analysis.rules}, bound from
 * {@code linksentry.rules.*}.
 *
 * <p>Kept out of the rule classes themselves so a threshold or a shortener domain
 * can change without a code change or redeploy, and so the rules stay framework-free
 * (they take plain constructor arguments, not a Spring properties type).
 *
 * @param excessiveUrlLength   {@code EXCESSIVE_URL_LENGTH} threshold
 * @param excessiveSubdomains  {@code EXCESSIVE_SUBDOMAINS} threshold
 * @param suspiciousKeywords   {@code SUSPICIOUS_KEYWORDS} word list
 * @param knownUrlShorteners   {@code KNOWN_URL_SHORTENER} domain list
 */
@Validated
@ConfigurationProperties(prefix = "linksentry.rules")
public record RulesProperties(
        @NotNull @Valid ExcessiveUrlLength excessiveUrlLength,
        @NotNull @Valid ExcessiveSubdomains excessiveSubdomains,
        @NotNull @Valid SuspiciousKeywords suspiciousKeywords,
        @NotNull @Valid KnownUrlShorteners knownUrlShorteners) {

    /** @param maxLength longest redacted display value tolerated without a finding, in characters */
    public record ExcessiveUrlLength(@Min(1) int maxLength) {}

    /** @param maxDepth deepest subdomain chain tolerated without a finding */
    public record ExcessiveSubdomains(@Min(0) int maxDepth) {}

    /** @param keywords lowercase words that are suspicious when they appear as a hostname label */
    public record SuspiciousKeywords(@NotEmpty List<@NotEmpty String> keywords) {}

    /** @param domains lowercase registrable domains of known URL-shortening services */
    public record KnownUrlShorteners(@NotEmpty List<@NotEmpty String> domains) {}
}
