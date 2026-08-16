package com.lyanhkhoa.linksentry.analysis.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExcessiveSubdomainsRuleTest {

    private final ExcessiveSubdomainsRule rule = new ExcessiveSubdomainsRule(3);

    @Test
    @DisplayName("more subdomain labels than the threshold produces a finding")
    void tooDeepProducesFinding() {
        NormalizedUrl url = urlWithSubdomains(List.of("login", "vietcombank", "com", "vn"));

        assertThat(rule.analyze(url)).isPresent();
        assertThat(rule.analyze(url).get().ruleId()).isEqualTo(ExcessiveSubdomainsRule.RULE_ID);
    }

    @Test
    @DisplayName("subdomain depth exactly at the threshold produces no finding")
    void exactlyAtThresholdProducesNoFinding() {
        NormalizedUrl url = urlWithSubdomains(List.of("a", "b", "c"));

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("no subdomains produces no finding")
    void noSubdomainsProducesNoFinding() {
        NormalizedUrl url = urlWithSubdomains(List.of());

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("rejects a negative threshold")
    void rejectsNegativeThreshold() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ExcessiveSubdomainsRule(-1));
    }

    private static NormalizedUrl urlWithSubdomains(List<String> subdomains) {
        String host = String.join(".", subdomains) + (subdomains.isEmpty() ? "" : ".") + "example.com";
        String raw = "https://" + host + "/";
        return new NormalizedUrl(
                raw, raw, "https", host, host, "example.com", subdomains, null, "/", false, false, false);
    }
}
