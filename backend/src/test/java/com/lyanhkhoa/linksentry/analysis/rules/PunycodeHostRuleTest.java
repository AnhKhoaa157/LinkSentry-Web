package com.lyanhkhoa.linksentry.analysis.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PunycodeHostRuleTest {

    private final PunycodeHostRule rule = new PunycodeHostRule();

    @Test
    @DisplayName("a punycode-encoded label produces a finding")
    void punycodeLabelProducesFinding() {
        NormalizedUrl url = urlWithAsciiHost("xn--80ak6aa92e.com", false);

        assertThat(rule.analyze(url)).isPresent();
        assertThat(rule.analyze(url).get().ruleId()).isEqualTo(PunycodeHostRule.RULE_ID);
    }

    @Test
    @DisplayName("a punycode label deeper in the host still produces a finding")
    void punycodeSubdomainProducesFinding() {
        NormalizedUrl url = urlWithAsciiHost("xn--80ak6aa92e.example.com", false);

        assertThat(rule.analyze(url)).isPresent();
    }

    @Test
    @DisplayName("a plain ASCII host produces no finding")
    void plainAsciiHostProducesNoFinding() {
        NormalizedUrl url = urlWithAsciiHost("example.com", false);

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("an IP literal host is never treated as punycode")
    void ipLiteralHostProducesNoFinding() {
        NormalizedUrl url = urlWithAsciiHost("203.0.113.5", true);

        assertThat(rule.analyze(url)).isEmpty();
    }

    private static NormalizedUrl urlWithAsciiHost(String asciiHost, boolean ipLiteral) {
        String raw = "https://" + asciiHost + "/";
        return new NormalizedUrl(
                raw, raw, "https", asciiHost, asciiHost, ipLiteral ? null : "example.com", List.of(), null, "/",
                false, false, ipLiteral);
    }
}
