package com.lyanhkhoa.linksentry.analysis.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KnownUrlShortenerRuleTest {

    private final KnownUrlShortenerRule rule = new KnownUrlShortenerRule(List.of("bit.ly", "tinyurl.com"));

    @Test
    @DisplayName("a known shortener domain produces a finding")
    void knownShortenerProducesFinding() {
        NormalizedUrl url = urlWithRegistrableDomain("bit.ly");

        assertThat(rule.analyze(url)).isPresent();
        assertThat(rule.analyze(url).get().ruleId()).isEqualTo(KnownUrlShortenerRule.RULE_ID);
    }

    @Test
    @DisplayName("matching is case-insensitive")
    void matchingIsCaseInsensitive() {
        NormalizedUrl url = urlWithRegistrableDomain("Bit.Ly");

        assertThat(rule.analyze(url)).isPresent();
    }

    @Test
    @DisplayName("an unrelated domain produces no finding")
    void unrelatedDomainProducesNoFinding() {
        NormalizedUrl url = urlWithRegistrableDomain("example.com");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("a null registrable domain produces no finding")
    void nullRegistrableDomainProducesNoFinding() {
        NormalizedUrl url = urlWithRegistrableDomain(null);

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("rejects an empty shortener list")
    void rejectsEmptyShortenerList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new KnownUrlShortenerRule(List.of()));
    }

    private static NormalizedUrl urlWithRegistrableDomain(String registrableDomain) {
        String host = registrableDomain == null ? "203.0.113.5" : registrableDomain;
        String raw = "https://" + host + "/x";
        return new NormalizedUrl(
                raw, raw, "https", host, host, registrableDomain, List.of(), null, "/x", false, false,
                registrableDomain == null);
    }
}
