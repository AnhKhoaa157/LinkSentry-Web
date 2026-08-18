package com.lyanhkhoa.linksentry.analysis.rules;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One curated brand entry in a {@link BrandRegistry}.
 *
 * <p>Every field is validated as curated, hand-maintained data — not something
 * discovered at runtime. {@code tokens} are exact-match pieces of a hostname label
 * (a dot- or hyphen-separated segment); {@code officialDomains} are registrable
 * domains this brand actually controls.
 *
 * @param id              stable machine-readable identifier for this brand entry,
 *                         unique within a {@link BrandRegistry}
 * @param displayName     human-readable name, safe to render in a finding
 * @param tokens          lowercase ASCII, alphanumeric hostname-label tokens that
 *                         identify this brand; must be non-empty and unique within
 *                         this brand
 * @param officialDomains lowercase ASCII registrable domains this brand actually
 *                         controls; must be non-empty and unique within this brand
 */
public record Brand(String id, String displayName, List<String> tokens, List<String> officialDomains) {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[a-z0-9]+$");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$");

    /**
     * Generic words rejected as a brand token at startup. A token this broad would
     * make {@code BrandDomainMismatchRule} and {@code BrandLookalikeRule} fire on
     * countless unrelated hostnames, turning a curated brand signal into noise —
     * see {@code docs/SECURITY_BOUNDARY.md} §8.
     */
    private static final Set<String> GENERIC_TOKEN_BLOCKLIST = Set.of(
            "bank", "secure", "login", "pay", "account", "verify", "update", "confirm", "signin", "password",
            "billing", "www", "mail", "app", "online", "web");

    public Brand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(tokens, "tokens");
        Objects.requireNonNull(officialDomains, "officialDomains");

        if (id.isBlank()) {
            throw new IllegalArgumentException("brand id must not be blank");
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("brand '" + id + "' displayName must not be blank");
        }
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("brand '" + id + "' must configure at least one token");
        }
        if (officialDomains.isEmpty()) {
            throw new IllegalArgumentException("brand '" + id + "' must configure at least one official domain");
        }

        tokens = List.copyOf(dedupOrThrow(id, "token", tokens, TOKEN_PATTERN));
        officialDomains = List.copyOf(dedupOrThrow(id, "official domain", officialDomains, DOMAIN_PATTERN));

        for (String token : tokens) {
            if (GENERIC_TOKEN_BLOCKLIST.contains(token)) {
                throw new IllegalArgumentException(
                        "brand '" + id + "' token '" + token + "' is too generic to be a brand-matching token");
            }
        }
    }

    private static Set<String> dedupOrThrow(String brandId, String label, List<String> values, Pattern pattern) {
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            Objects.requireNonNull(value, label);
            if (value.isBlank()) {
                throw new IllegalArgumentException("brand '" + brandId + "' has a blank " + label);
            }
            String normalized = value.toLowerCase(Locale.ROOT);
            if (!normalized.equals(value) || !pattern.matcher(value).matches()) {
                throw new IllegalArgumentException(
                        "brand '" + brandId + "' " + label + " '" + value + "' must be lowercase ASCII");
            }
            if (!seen.add(value)) {
                throw new IllegalArgumentException(
                        "brand '" + brandId + "' has a duplicate " + label + " '" + value + "'");
            }
        }
        return seen;
    }
}
