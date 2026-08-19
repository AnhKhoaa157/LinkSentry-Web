package com.lyanhkhoa.linksentry.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.IDN;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the invariants {@link DomainFeatures} enforces today: it is built once
 * from {@code asciiHost} alone, its labels preserve source order, its token
 * vocabulary matches the tokenization {@code BrandDomainMismatchRule} and {@code
 * BrandLookalikeRule} relied on before this type existed, and it never touches
 * anything but the ASCII host string.
 */
class DomainFeaturesTest {

    @Test
    @DisplayName("labels preserve source order, left to right")
    void labelsPreserveSourceOrder() {
        DomainFeatures features = DomainFeatures.fromAsciiHost("login.vietcombank.com.vn.evil-domain.xyz");

        assertThat(features.labels()).extracting(DomainFeatures.Label::value)
                .containsExactly("login", "vietcombank", "com", "vn", "evil-domain", "xyz");
    }

    @Test
    @DisplayName("host tokens split on both '.' and '-', deduplicated")
    void hostTokensSplitOnDotsAndHyphens() {
        DomainFeatures features = DomainFeatures.fromAsciiHost("vietcombank-secure-login.xyz");

        assertThat(features.hostTokens()).containsExactlyInAnyOrder("vietcombank", "secure", "login", "xyz");
    }

    @Test
    @DisplayName("a token repeated across labels appears once in hostTokens")
    void repeatedTokenIsDeduplicated() {
        DomainFeatures features = DomainFeatures.fromAsciiHost("pay.pay.example.com");

        assertThat(features.hostTokens()).containsExactlyInAnyOrder("pay", "example", "com");
    }

    @Test
    @DisplayName("a label without a hyphen collapses to itself")
    void labelWithoutHyphenCollapsesToItself() {
        DomainFeatures features = DomainFeatures.fromAsciiHost("vietcombank.xyz");

        DomainFeatures.Label label = features.labels().get(0);
        assertThat(label.value()).isEqualTo("vietcombank");
        assertThat(label.hyphenCollapsed()).isEqualTo("vietcombank");
    }

    @Test
    @DisplayName("a hyphenated label collapses to the token with hyphens removed")
    void hyphenatedLabelCollapses() {
        DomainFeatures features = DomainFeatures.fromAsciiHost("viet-com-bank.xyz");

        DomainFeatures.Label label = features.labels().get(0);
        assertThat(label.value()).isEqualTo("viet-com-bank");
        assertThat(label.hyphenCollapsed()).isEqualTo("vietcombank");
    }

    @Test
    @DisplayName("a Punycode label carries its decoded Unicode form")
    void punycodeLabelDecodesToUnicode() {
        String confusableLabel = "vietc" + 'о' + "mbank"; // Cyrillic о (U+043E)
        String encodedLabel = IDN.toASCII(confusableLabel, IDN.USE_STD3_ASCII_RULES);
        DomainFeatures features = DomainFeatures.fromAsciiHost(encodedLabel + ".xyz");

        DomainFeatures.Label label = features.labels().get(0);
        assertThat(label.value()).isEqualTo(encodedLabel);
        assertThat(label.isPunycode()).isTrue();
        assertThat(label.punycodeDecoded()).isEqualTo(confusableLabel);
    }

    @Test
    @DisplayName("a plain ASCII label carries no Punycode metadata")
    void plainAsciiLabelHasNoPunycodeMetadata() {
        DomainFeatures features = DomainFeatures.fromAsciiHost("example.com");

        assertThat(features.labels()).allSatisfy(label -> {
            assertThat(label.isPunycode()).isFalse();
            assertThat(label.punycodeDecoded()).isNull();
        });
    }

    @Test
    @DisplayName("labels and hostTokens are immutable")
    void labelsAndHostTokensAreImmutable() {
        DomainFeatures features = DomainFeatures.fromAsciiHost("example.com");

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> features.labels().add(new DomainFeatures.Label("x", "x", null)));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> features.hostTokens().add("x"));
    }

    @Test
    @DisplayName("a defensive copy is taken of caller-supplied labels and tokens")
    void constructorDefensivelyCopiesInput() {
        java.util.List<DomainFeatures.Label> mutableLabels =
                new java.util.ArrayList<>(List.of(new DomainFeatures.Label("example", "example", null)));
        java.util.Set<String> mutableTokens = new java.util.HashSet<>(Set.of("example"));

        DomainFeatures features = new DomainFeatures(mutableLabels, mutableTokens);
        mutableLabels.clear();
        mutableTokens.clear();

        assertThat(features.labels()).hasSize(1);
        assertThat(features.hostTokens()).containsExactly("example");
    }

    @Test
    @DisplayName("equal asciiHost values produce equal DomainFeatures")
    void equalAsciiHostsProduceEqualFeatures() {
        String host = "login.vietcombank.com.vn.evil-domain.xyz";
        DomainFeatures first = DomainFeatures.fromAsciiHost(host);
        // A distinct String instance with the same content, not the same literal
        // reference, so this asserts value equality rather than identity.
        DomainFeatures second = DomainFeatures.fromAsciiHost(new String(host.toCharArray()));

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("requires a non-null ASCII host")
    void requiresAsciiHost() {
        assertThatNullPointerException().isThrownBy(() -> DomainFeatures.fromAsciiHost(null));
    }

    @Test
    @DisplayName("NormalizedUrl requires a non-null DomainFeatures")
    void normalizedUrlRequiresDomainFeatures() {
        assertThatNullPointerException().isThrownBy(() -> new NormalizedUrl(
                "https://example.com/", "https://example.com/", "https", "example.com", "example.com", null,
                "example.com", List.of(), null, "/", false, false, false));
    }
}
