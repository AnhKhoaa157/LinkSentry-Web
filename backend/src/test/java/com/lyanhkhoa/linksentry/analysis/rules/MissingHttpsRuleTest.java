package com.lyanhkhoa.linksentry.analysis.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.analysis.domain.DomainFeatures;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MissingHttpsRuleTest {

    private final MissingHttpsRule rule = new MissingHttpsRule();

    @Test
    @DisplayName("http produces a finding with the rule's id")
    void httpProducesFinding() {
        NormalizedUrl url = urlWithScheme("http", "/", false);

        Optional<RuleFinding> finding = rule.analyze(url);

        assertThat(finding).isPresent();
        assertThat(finding.get().ruleId()).isEqualTo(MissingHttpsRule.RULE_ID);
        assertThat(finding.get().ruleId()).isEqualTo(rule.id());
    }

    @Test
    @DisplayName("https produces no finding")
    void httpsProducesNoFinding() {
        NormalizedUrl url = urlWithScheme("https", "/", false);

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("an http url with a path and query still produces exactly one finding, without leaking query text")
    void httpWithPathAndQueryProducesOneFindingWithoutLeakingQuery() {
        NormalizedUrl url = urlWithScheme("http", "/reset-password", true);

        Optional<RuleFinding> finding = rule.analyze(url);

        assertThat(finding).isPresent();
        RuleFinding value = finding.get();
        assertThat(value.ruleId()).isEqualTo(MissingHttpsRule.RULE_ID);
        assertThat(value.explanation()).doesNotContain("token=secret");
        assertThat(value.explanation()).doesNotContain(url.path());
        assertThat(value.evidence()).isNull();
    }

    private static NormalizedUrl urlWithScheme(String scheme, String path, boolean queryPresent) {
        String raw = scheme + "://example.com" + path + (queryPresent ? "?token=secret" : "");
        String redacted = scheme + "://example.com" + path;
        return new NormalizedUrl(
                raw,
                redacted,
                scheme,
                "example.com",
                "example.com",
                DomainFeatures.fromAsciiHost("example.com"),
                "example.com",
                List.of(),
                null,
                path,
                queryPresent,
                false,
                false);
    }
}
