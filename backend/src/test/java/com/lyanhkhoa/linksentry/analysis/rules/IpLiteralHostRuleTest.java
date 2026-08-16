package com.lyanhkhoa.linksentry.analysis.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IpLiteralHostRuleTest {

    private final IpLiteralHostRule rule = new IpLiteralHostRule();

    @Test
    @DisplayName("an IPv4 literal host produces a finding")
    void ipv4LiteralProducesFinding() {
        NormalizedUrl url = urlWithHost("203.0.113.5", null, true);

        assertThat(rule.analyze(url)).isPresent();
        assertThat(rule.analyze(url).get().ruleId()).isEqualTo(IpLiteralHostRule.RULE_ID);
    }

    @Test
    @DisplayName("an IPv6 literal host produces a finding")
    void ipv6LiteralProducesFinding() {
        NormalizedUrl url = urlWithHost("[2001:db8::1]", null, true);

        assertThat(rule.analyze(url)).isPresent();
    }

    @Test
    @DisplayName("a normal domain name produces no finding")
    void domainNameProducesNoFinding() {
        NormalizedUrl url = urlWithHost("example.com", "example.com", false);

        assertThat(rule.analyze(url)).isEmpty();
    }

    private static NormalizedUrl urlWithHost(String host, String registrableDomain, boolean ipLiteral) {
        String raw = "https://" + host + "/";
        return new NormalizedUrl(
                raw, raw, "https", host, host, registrableDomain, List.of(), null, "/", false, false, ipLiteral);
    }
}
