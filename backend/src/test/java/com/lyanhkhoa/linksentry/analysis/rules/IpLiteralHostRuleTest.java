package com.lyanhkhoa.linksentry.analysis.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.analysis.domain.DomainFeatures;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.normalization.IpAddressScope;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IpLiteralHostRuleTest {

    private final IpLiteralHostRule rule = new IpLiteralHostRule();

    @Test
    @DisplayName("a public IPv4 literal host produces a finding")
    void ipv4LiteralProducesFinding() {
        NormalizedUrl url = urlWithHost("8.8.8.8", null, true);

        assertThat(rule.analyze(url)).isPresent();
        assertThat(rule.analyze(url).get().ruleId()).isEqualTo(IpLiteralHostRule.RULE_ID);
        assertThat(rule.analyze(url).get().evidence()).isEqualTo(IpAddressScope.PUBLIC.evidence());
    }

    @Test
    @DisplayName("a public IPv6 literal host produces a finding")
    void ipv6LiteralProducesFinding() {
        NormalizedUrl url = urlWithHost("[2606:4700:4700::1111]", null, true);

        assertThat(rule.analyze(url)).isPresent();
    }

    @Test
    @DisplayName("a special-use literal is left for SPECIAL_USE_OR_PRIVATE_HOST")
    void specialUseLiteralProducesNoFinding() {
        NormalizedUrl url = urlWithHost("203.0.113.5", null, true);

        assertThat(rule.analyze(url)).isEmpty();
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
                raw, raw, "https", host, host, DomainFeatures.fromAsciiHost(host), registrableDomain, List.of(),
                null, "/", false, false, ipLiteral);
    }
}
