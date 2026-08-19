package com.lyanhkhoa.linksentry.analysis.rules;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.DomainFeatures;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import java.net.IDN;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Flags a hostname label that is a conservative, deterministic lookalike of a
 * configured {@link Brand} token, on a domain that brand does not control.
 *
 * <p>This rule is deliberately narrower and weaker-signalled than
 * {@link BrandDomainMismatchRule}: it never runs for a brand that rule already
 * identified via an exact token match, and it only fires on three bounded,
 * explainable obfuscation techniques — never a broad fuzzy-match or a stripped
 * "non-ASCII" heuristic:
 *
 * <ol>
 *   <li><strong>One-character ASCII typo.</strong> A hostname label is exactly one
 *       insertion, deletion, substitution, or adjacent-character transposition away
 *       from a configured token of length 5 or more (e.g. {@code v1etcombank} or
 *       {@code vietcombnak} for {@code vietcombank}). Tokens shorter than 5
 *       characters are excluded from this signal: a single edit on a short token
 *       (e.g. {@code acb}) collides with too many unrelated words to be a reliable
 *       signal.
 *   <li><strong>Hyphen-separated label collapse.</strong> A label containing a
 *       hyphen collapses, once hyphens are removed, to exactly a configured token of
 *       length 5 or more (e.g. {@code viet-com-bank} collapses to {@code
 *       vietcombank}). Only an exact match after collapsing counts — this is not
 *       combined with the typo signal. The same length floor as the typo signal
 *       applies for the same reason: a short token (e.g. {@code acb}) collapses out
 *       of too many unrelated hyphenated labels (e.g. {@code a-c-b}) to be a
 *       reliable signal.
 *   <li><strong>Unicode confusable substitution.</strong> A Punycode label
 *       ({@code xn--...}) is decoded locally via {@link IDN#toUnicode} — no network
 *       call, no external service — and every character is looked up in a small,
 *       explicit, hand-curated map of common Latin/Cyrillic/Greek lookalike letters
 *       ({@link #CONFUSABLE_MAP}). If every character maps to plain ASCII and the
 *       substituted string exactly equals a configured token, and at least one
 *       character actually needed substitution, this fires. A label containing any
 *       character outside that small map is left alone rather than guessed at.
 * </ol>
 *
 * <p>Only {@link NormalizedUrl#asciiHost()} is inspected. The path, query,
 * fragment, credentials, DNS, page content, redirects and network data are never
 * consulted — see {@code docs/SECURITY_BOUNDARY.md}. When several configured
 * brands could match, exactly one is chosen — the first, in {@link BrandRegistry}
 * order, with a qualifying label — and this rule never contributes more than one
 * finding. Its evidence names only the matched brand's static display name, its
 * official domain(s), and a generic signal-type label; it never echoes the
 * submitted hostname or the matched label.
 */
public final class BrandLookalikeRule implements AnalysisRule {

    /** Stable machine-readable identifier for this rule. */
    public static final String RULE_ID = "BRAND_LOOKALIKE_HOSTNAME";

    /** Weaker, inferential signal than an exact brand/domain mismatch. */
    private static final Severity SEVERITY = Severity.MEDIUM;

    private static final int POINTS = 20;

    /**
     * Shared floor for the typo and hyphen-collapse signals: below this length, a
     * single ASCII edit — or a hyphen split that happens to collapse back to the
     * token — collides with too many unrelated short words to be a reliable
     * signal. Does not apply to the confusable-character signal, which is already
     * an exact match after substitution, not an edit-distance heuristic.
     */
    private static final int MIN_TOKEN_LENGTH_FOR_OBFUSCATION_SIGNALS = 5;

    private static final String TITLE = "Hostname closely resembles a known brand";

    private static final String EXPLANATION =
            "Part of this address closely resembles the name of a known brand — for "
                    + "example a single swapped character or a lookalike letter from another "
                    + "alphabet — but the domain actually registered for this link does not "
                    + "belong to that brand. This is a common technique for impersonating a "
                    + "trusted organisation's login or account pages. This does not prove the "
                    + "link is malicious, but it is worth verifying before entering any "
                    + "credentials.";

    private static final String SIGNAL_ONE_CHARACTER_TYPO = "one-character typo";
    private static final String SIGNAL_HYPHEN_OBFUSCATION = "hyphen-separated obfuscation";
    private static final String SIGNAL_LOOKALIKE_CHARACTER = "lookalike character";

    /**
     * Small, explicit, hand-curated map of lowercase Latin/Cyrillic/Greek letters
     * that are visually indistinguishable (or nearly so) from a Latin ASCII letter.
     * Deliberately not exhaustive: an unmapped non-ASCII character is left alone
     * rather than guessed at, which is why this is a conservative signal and not a
     * general "strip non-ASCII" heuristic.
     */
    private static final Map<Integer, Character> CONFUSABLE_MAP = buildConfusableMap();

    private final BrandRegistry registry;

    public BrandLookalikeRule(BrandRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

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
        String registrableDomain = url.registrableDomain();
        if (registrableDomain == null) {
            return Optional.empty();
        }

        Set<String> exactHostTokens = url.domainFeatures().hostTokens();
        List<DomainFeatures.Label> labels = url.domainFeatures().labels();

        for (Brand brand : registry.brands()) {
            if (brand.officialDomains().contains(registrableDomain)) {
                continue;
            }
            boolean alreadyExactlyIdentified = brand.tokens().stream().anyMatch(exactHostTokens::contains);
            if (alreadyExactlyIdentified) {
                continue;
            }

            Optional<String> signal = findLookalikeSignal(labels, brand);
            if (signal.isPresent()) {
                String evidence = brand.displayName() + " official domain(s): "
                        + String.join(", ", brand.officialDomains()) + "; signal: " + signal.get();
                return Optional.of(RuleFinding.of(RULE_ID, SEVERITY, POINTS, TITLE, EXPLANATION, evidence));
            }
        }

        return Optional.empty();
    }

    private Optional<String> findLookalikeSignal(List<DomainFeatures.Label> labels, Brand brand) {
        for (DomainFeatures.Label label : labels) {
            if (label.value().isEmpty()) {
                continue;
            }

            for (String token : brand.tokens()) {
                if (token.length() >= MIN_TOKEN_LENGTH_FOR_OBFUSCATION_SIGNALS
                        && isSingleEditAway(label.value(), token)) {
                    return Optional.of(SIGNAL_ONE_CHARACTER_TYPO);
                }
            }

            if (label.value().indexOf('-') >= 0) {
                for (String token : brand.tokens()) {
                    if (token.length() >= MIN_TOKEN_LENGTH_FOR_OBFUSCATION_SIGNALS
                            && label.hyphenCollapsed().equals(token)) {
                        return Optional.of(SIGNAL_HYPHEN_OBFUSCATION);
                    }
                }
            }

            if (label.isPunycode()) {
                Optional<String> normalized = confusableNormalize(label.punycodeDecoded());
                if (normalized.isPresent()) {
                    for (String token : brand.tokens()) {
                        if (normalized.get().equals(token)) {
                            return Optional.of(SIGNAL_LOOKALIKE_CHARACTER);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Maps every character of {@code unicodeLabel} through {@link #CONFUSABLE_MAP},
     * falling back to the character itself when it is already plain ASCII.
     * Returns empty when the label contains a non-ASCII character absent from the
     * map (refuse to guess) or when no character actually needed substitution
     * (nothing to flag).
     */
    private static Optional<String> confusableNormalize(String unicodeLabel) {
        StringBuilder normalized = new StringBuilder(unicodeLabel.length());
        boolean sawConfusable = false;
        int index = 0;
        while (index < unicodeLabel.length()) {
            int codePoint = unicodeLabel.codePointAt(index);
            Character replacement = CONFUSABLE_MAP.get(codePoint);
            if (replacement != null) {
                normalized.append(replacement.charValue());
                sawConfusable = true;
            } else if (codePoint < 128) {
                normalized.append((char) codePoint);
            } else {
                return Optional.empty();
            }
            index += Character.charCount(codePoint);
        }
        return sawConfusable ? Optional.of(normalized.toString()) : Optional.empty();
    }

    /**
     * True when {@code a} and {@code b} differ by exactly one ASCII edit: an
     * insertion, a deletion, a substitution, or an adjacent-character
     * transposition. Runs in linear time without a full Levenshtein DP table,
     * since only a distance of exactly 1 is ever of interest here.
     */
    private static boolean isSingleEditAway(String a, String b) {
        if (a.equals(b)) {
            return false;
        }
        int lengthDelta = a.length() - b.length();
        if (Math.abs(lengthDelta) > 1) {
            return false;
        }
        if (lengthDelta == 0) {
            return isSubstitutionOrTransposition(a, b);
        }
        String longer = lengthDelta > 0 ? a : b;
        String shorter = lengthDelta > 0 ? b : a;
        return isSingleInsertionOrDeletion(longer, shorter);
    }

    private static boolean isSubstitutionOrTransposition(String a, String b) {
        int firstDiff = -1;
        int secondDiff = -1;
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) != b.charAt(i)) {
                if (firstDiff == -1) {
                    firstDiff = i;
                } else if (secondDiff == -1) {
                    secondDiff = i;
                } else {
                    return false;
                }
            }
        }
        if (secondDiff == -1) {
            return firstDiff != -1;
        }
        return secondDiff == firstDiff + 1
                && a.charAt(firstDiff) == b.charAt(secondDiff)
                && a.charAt(secondDiff) == b.charAt(firstDiff);
    }

    private static boolean isSingleInsertionOrDeletion(String longer, String shorter) {
        int i = 0;
        int j = 0;
        boolean usedSkip = false;
        while (i < shorter.length() && j < longer.length()) {
            if (shorter.charAt(i) == longer.charAt(j)) {
                i++;
                j++;
                continue;
            }
            if (usedSkip) {
                return false;
            }
            usedSkip = true;
            j++;
        }
        return true;
    }

    private static Map<Integer, Character> buildConfusableMap() {
        Map<Integer, Character> map = new HashMap<>();
        // Cyrillic lowercase letters visually identical (or near-identical) to Latin.
        map.put(0x0430, 'a'); // а
        map.put(0x0435, 'e'); // е
        map.put(0x043E, 'o'); // о
        map.put(0x0440, 'p'); // р
        map.put(0x0441, 'c'); // с
        map.put(0x0443, 'y'); // у
        map.put(0x0445, 'x'); // х
        map.put(0x0456, 'i'); // і
        map.put(0x0455, 's'); // ѕ
        map.put(0x0458, 'j'); // ј
        map.put(0x0501, 'd'); // ԁ
        // Greek lowercase letters visually identical (or near-identical) to Latin.
        map.put(0x03B1, 'a'); // α
        map.put(0x03BF, 'o'); // ο
        map.put(0x03C1, 'p'); // ρ
        map.put(0x03B9, 'i'); // ι
        map.put(0x03BA, 'k'); // κ
        map.put(0x03C4, 't'); // τ
        map.put(0x03C5, 'y'); // υ
        return Map.copyOf(map);
    }
}
