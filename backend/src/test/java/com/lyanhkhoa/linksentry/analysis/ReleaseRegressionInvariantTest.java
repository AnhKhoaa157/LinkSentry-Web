package com.lyanhkhoa.linksentry.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisResult;
import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ReleaseRegressionInvariantTest {

    private static final Pattern SAFE_VERDICT =
            Pattern.compile("(?i)\\b(?:is|are|looks?|seems?|appears?)\\s+safe\\b");

    private static final Map<String, String> REPRESENTATIVE_INPUT_BY_RULE = Map.ofEntries(
            Map.entry("MISSING_HTTPS", "http://example.com/"),
            Map.entry("IP_LITERAL_HOST", "https://8.8.8.8/"),
            Map.entry("SPECIAL_USE_OR_PRIVATE_HOST", "https://10.0.0.1/"),
            Map.entry("EXCESSIVE_URL_LENGTH", "https://example.com/" + "a".repeat(81)),
            Map.entry("EXCESSIVE_SUBDOMAINS", "https://a.b.c.d.example.com/"),
            Map.entry("SUSPICIOUS_KEYWORDS", "https://login.example.com/"),
            Map.entry("BRAND_DOMAIN_MISMATCH", "https://vietcombank.evil-domain.xyz/"),
            Map.entry("BRAND_LOOKALIKE_HOSTNAME", "https://v1etcombank.evil-domain.xyz/"),
            Map.entry("PUNYCODE_HOST", "https://xn--mnchen-3ya.example/"),
            Map.entry("ENCODED_CHARACTERS", "https://example.com/reset%20password"),
            Map.entry("KNOWN_URL_SHORTENER", "https://bit.ly/release"));

    private final UrlAnalyzer analyzer = AnalyzerFixture.productionAnalyzer();

    @Test
    void representativeM1ThroughM4InputsRemainDeterministic() {
        List<String> inputs = List.of(
                "https://example.com/",
                "https://xn--mnchen-3ya.example/",
                "https://v1etcombank.evil-domain.xyz/reset%20password",
                "https://203.0.113.5/",
                "https://[fd00::1]/path?token=DETERMINISM_SECRET#fragment");

        for (String input : inputs) {
            AnalysisResult first = analyzer.analyze(input);
            AnalysisResult second = analyzer.analyze(input);

            assertThat(second).as(input).isEqualTo(first);
        }
    }

    @Test
    void everyConfiguredRuleHasRepresentativeCoverageWithoutASafeVerdict() {
        assertThat(REPRESENTATIVE_INPUT_BY_RULE.keySet())
                .containsExactlyInAnyOrderElementsOf(
                        AnalyzerFixture.productionRules().stream().map(AnalysisRule::id).toList());

        REPRESENTATIVE_INPUT_BY_RULE.forEach((ruleId, input) -> {
            RuleFinding finding = analyzer.analyze(input).findings().stream()
                    .filter(candidate -> candidate.ruleId().equals(ruleId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(ruleId + " did not fire for its release fixture"));

            findingCopy(finding).forEach(copy -> assertThat(SAFE_VERDICT.matcher(copy).find())
                    .as("%s copy must remain risk-oriented: %s", ruleId, copy)
                    .isFalse());
        });

        assertThat(RiskLevel.values()).extracting(Enum::name).doesNotContain("SAFE");
    }

    @Test
    void secretsNeverReachRedactedDisplayFindingsOrInvalidUrlErrors() {
        String querySecret = "M5_QUERY_SECRET";
        String fragmentSecret = "M5_FRAGMENT_SECRET";
        AnalysisResult result = analyzer.analyze(
                "https://v1etcombank.evil-domain.xyz/reset%20password?token="
                        + querySecret
                        + "#"
                        + fragmentSecret);

        assertThat(result.normalizedUrl().redactedDisplayValue())
                .doesNotContain(querySecret, fragmentSecret);
        result.findings().forEach(finding -> findingCopy(finding)
                .forEach(copy -> assertThat(copy).doesNotContain(querySecret, fragmentSecret)));

        String credentialSecret = "M5_CREDENTIAL_SECRET";
        assertThatThrownBy(() -> analyzer.analyze(
                        "https://user:" + credentialSecret + "@example.com/?token=" + querySecret + "#" + fragmentSecret))
                .isExactlyInstanceOf(InvalidUrlException.class)
                .hasMessage("URL must have a valid host")
                .hasMessageNotContaining(credentialSecret)
                .hasMessageNotContaining(querySecret)
                .hasMessageNotContaining(fragmentSecret)
                .hasNoCause();
    }

    private static Stream<String> findingCopy(RuleFinding finding) {
        return Stream.of(finding.title(), finding.explanation(), finding.evidence()).filter(Objects::nonNull);
    }
}
