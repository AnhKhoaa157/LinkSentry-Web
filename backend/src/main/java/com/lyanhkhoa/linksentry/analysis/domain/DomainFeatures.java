package com.lyanhkhoa.linksentry.analysis.domain;

import com.lyanhkhoa.linksentry.analysis.normalization.IdnaProcessor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Precomputed, host-only lexical features of a {@link NormalizedUrl#asciiHost()}.
 *
 * <p>Built exactly once, from {@code asciiHost} alone, by
 * {@link com.lyanhkhoa.linksentry.analysis.normalization.DefaultUrlNormalizer} via
 * {@link #fromAsciiHost(String)} and stored as a required {@link NormalizedUrl}
 * component. This exists so that {@link AnalysisRule}s which independently need the
 * same tokenization, hyphen-collapse, or Punycode-decoding work — currently
 * {@code BrandDomainMismatchRule} and {@code BrandLookalikeRule} — read the same
 * precomputed data instead of each recomputing it on every call.
 *
 * <p>Derived from {@code asciiHost} only. Never holds {@code rawInput}, query
 * values, fragment data, credentials, or path data — see
 * {@code docs/SECURITY_BOUNDARY.md}. Building it performs no I/O: {@link
 * {@link IdnaProcessor#toUnicode(String)} is a pure local conversion, the same
 * offline guarantee the brand rules rely on.
 *
 * @param labels     the ASCII host split on {@code .}, in source order, each paired
 *                   with its precomputed obfuscation-detection features
 * @param hostTokens every label further split on {@code -}, deduplicated; the
 *                   exact-match vocabulary {@code BrandDomainMismatchRule} and
 *                   {@code BrandLookalikeRule} compare a brand's tokens against
 */
public record DomainFeatures(List<Label> labels, Set<String> hostTokens) {

    private static final IdnaProcessor IDNA_PROCESSOR = new IdnaProcessor();

    public DomainFeatures {
        Objects.requireNonNull(labels, "labels");
        Objects.requireNonNull(hostTokens, "hostTokens");
        labels = List.copyOf(labels);
        hostTokens = Set.copyOf(hostTokens);
    }

    /**
     * One ASCII host label plus its precomputed obfuscation-detection features.
     *
     * @param value           the label exactly as it appears in {@code asciiHost}
     * @param hyphenCollapsed {@code value} with every {@code -} removed; equal to
     *                        {@code value} itself when it contains no hyphen
     * @param punycodeDecoded {@link IdnaProcessor#toUnicode(String)} applied to
     *                        {@code value} when it is a Punycode label ({@code
     *                        xn--} prefixed, matched case-insensitively); {@code
     *                        null} when {@code value} is not a Punycode label
     */
    public record Label(String value, String hyphenCollapsed, String punycodeDecoded) {

        public Label {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(hyphenCollapsed, "hyphenCollapsed");
        }

        /** Whether this label carries Punycode metadata. */
        public boolean isPunycode() {
            return punycodeDecoded != null;
        }
    }

    /**
     * Builds the features of {@code asciiHost} in a single pass. Called exactly once
     * per scan, by the normalizer, from the already-lowercased ASCII form of the
     * host — never from {@code rawInput} or any other field.
     */
    public static DomainFeatures fromAsciiHost(String asciiHost) {
        Objects.requireNonNull(asciiHost, "asciiHost");

        String[] rawLabels = asciiHost.split("\\.", -1);
        List<Label> labels = new ArrayList<>(rawLabels.length);
        Set<String> hostTokens = new LinkedHashSet<>();

        for (String rawLabel : rawLabels) {
            labels.add(new Label(rawLabel, rawLabel.replace("-", ""), punycodeDecodedOrNull(rawLabel)));
            for (String part : rawLabel.split("-", -1)) {
                if (!part.isEmpty()) {
                    hostTokens.add(part);
                }
            }
        }

        return new DomainFeatures(labels, hostTokens);
    }

    private static String punycodeDecodedOrNull(String label) {
        if (!label.toLowerCase(Locale.ROOT).startsWith("xn--")) {
            return null;
        }
        return IDNA_PROCESSOR.toUnicode(label);
    }
}
