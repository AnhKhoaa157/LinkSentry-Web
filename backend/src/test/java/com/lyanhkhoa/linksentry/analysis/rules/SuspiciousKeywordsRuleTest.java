package com.lyanhkhoa.linksentry.analysis.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.lyanhkhoa.linksentry.analysis.domain.DomainFeatures;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SuspiciousKeywordsRuleTest {

    private final SuspiciousKeywordsRule rule = new SuspiciousKeywordsRule(List.of("login", "verify"));

    @Test
    @DisplayName("a suspicious word in a subdomain label produces a finding")
    void suspiciousSubdomainProducesFinding() {
        NormalizedUrl url = urlWithSubdomainsAndPath(List.of("login"), "/");

        assertThat(rule.analyze(url)).isPresent();
        assertThat(rule.analyze(url).get().ruleId()).isEqualTo(SuspiciousKeywordsRule.RULE_ID);
    }

    @Test
    @DisplayName("matching is case-insensitive")
    void matchingIsCaseInsensitive() {
        NormalizedUrl url = urlWithSubdomainsAndPath(List.of("VeRiFy"), "/");

        assertThat(rule.analyze(url)).isPresent();
    }

    @Test
    @DisplayName("the same word appearing only in the path produces no finding")
    void wordInPathOnlyProducesNoFinding() {
        NormalizedUrl url = urlWithSubdomainsAndPath(List.of(), "/login");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("no matching subdomains produces no finding")
    void noMatchProducesNoFinding() {
        NormalizedUrl url = urlWithSubdomainsAndPath(List.of("www"), "/");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("rejects an empty keyword list")
    void rejectsEmptyKeywordList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SuspiciousKeywordsRule(List.of()));
    }

    private static NormalizedUrl urlWithSubdomainsAndPath(List<String> subdomains, String path) {
        String host = String.join(".", subdomains) + (subdomains.isEmpty() ? "" : ".") + "example.com";
        String raw = "https://" + host + path;
        return new NormalizedUrl(
                raw, raw, "https", host, host, DomainFeatures.fromAsciiHost(host), "example.com", subdomains, null,
                path, false, false, false);
    }
}
