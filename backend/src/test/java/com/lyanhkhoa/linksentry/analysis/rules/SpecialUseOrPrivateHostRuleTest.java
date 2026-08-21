package com.lyanhkhoa.linksentry.analysis.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.analysis.domain.DomainFeatures;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.normalization.IpAddressScope;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpecialUseOrPrivateHostRuleTest {

    private final SpecialUseOrPrivateHostRule rule = new SpecialUseOrPrivateHostRule();

    @ParameterizedTest
    @CsvSource({
        "10.0.0.1, PRIVATE",
        "127.0.0.1, LOOPBACK",
        "169.254.1.1, LINK_LOCAL",
        "203.0.113.5, DOCUMENTATION",
        "100.64.0.1, SPECIAL_USE",
        "[fc00::1], UNIQUE_LOCAL",
        "[fe80::1], LINK_LOCAL",
        "[2001:db8::1], DOCUMENTATION",
        "[2002::1], SPECIAL_USE"
    })
    @DisplayName("private and special-use literals produce one scope-specific finding")
    void specialUseLiteralProducesFinding(String host, IpAddressScope scope) {
        NormalizedUrl url = urlWithHost(host, true);

        assertThat(rule.analyze(url)).isPresent();
        assertThat(rule.analyze(url).get().ruleId()).isEqualTo(SpecialUseOrPrivateHostRule.RULE_ID);
        assertThat(rule.analyze(url).get().evidence()).isEqualTo(scope.evidence());
    }

    @Test
    @DisplayName("a public literal produces no special-use finding")
    void publicLiteralProducesNoFinding() {
        assertThat(rule.analyze(urlWithHost("8.8.8.8", true))).isEmpty();
    }

    @Test
    @DisplayName("a hostname produces no special-use finding")
    void hostnameProducesNoFinding() {
        assertThat(rule.analyze(urlWithHost("example.com", false))).isEmpty();
    }

    private static NormalizedUrl urlWithHost(String host, boolean ipLiteral) {
        String raw = "https://" + host + "/";
        return new NormalizedUrl(
                raw,
                raw,
                "https",
                host,
                host,
                DomainFeatures.fromAsciiHost(host),
                ipLiteral ? null : "example.com",
                List.of(),
                null,
                "/",
                false,
                false,
                ipLiteral);
    }
}
