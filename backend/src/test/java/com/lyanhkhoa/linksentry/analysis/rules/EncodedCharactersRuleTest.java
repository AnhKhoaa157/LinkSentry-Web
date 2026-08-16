package com.lyanhkhoa.linksentry.analysis.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EncodedCharactersRuleTest {

    private final EncodedCharactersRule rule = new EncodedCharactersRule();

    @Test
    @DisplayName("a percent-encoded sequence in the path produces a finding")
    void encodedPathProducesFinding() {
        NormalizedUrl url = urlWithPath("/account%20details");

        assertThat(rule.analyze(url)).isPresent();
        assertThat(rule.analyze(url).get().ruleId()).isEqualTo(EncodedCharactersRule.RULE_ID);
    }

    @Test
    @DisplayName("a plain path produces no finding")
    void plainPathProducesNoFinding() {
        NormalizedUrl url = urlWithPath("/account/details");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("a bare percent sign without two hex digits is not treated as encoding")
    void barePercentSignIsNotEncoding() {
        NormalizedUrl url = urlWithPath("/100%-off");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("the finding never quotes the path or query text")
    void findingDoesNotLeakPathOrQuery() {
        NormalizedUrl url = urlWithPath("/reset%2Dpassword");

        String explanation = rule.analyze(url).orElseThrow().explanation();
        assertThat(explanation).doesNotContain("/reset%2Dpassword");
        assertThat(explanation).doesNotContain("token=secret");
    }

    private static NormalizedUrl urlWithPath(String path) {
        String raw = "https://example.com" + path;
        return new NormalizedUrl(
                raw, raw, "https", "example.com", "example.com", "example.com", List.of(), null, path, false, false,
                false);
    }
}
