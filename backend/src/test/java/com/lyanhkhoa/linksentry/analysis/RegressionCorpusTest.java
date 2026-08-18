package com.lyanhkhoa.linksentry.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisResult;
import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.DefaultUrlAnalyzer;
import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import com.lyanhkhoa.linksentry.analysis.normalization.DefaultUrlNormalizer;
import com.lyanhkhoa.linksentry.analysis.normalization.UrlNormalizer;
import com.lyanhkhoa.linksentry.analysis.rules.Brand;
import com.lyanhkhoa.linksentry.analysis.rules.BrandDomainMismatchRule;
import com.lyanhkhoa.linksentry.analysis.rules.BrandRegistry;
import com.lyanhkhoa.linksentry.analysis.rules.EncodedCharactersRule;
import com.lyanhkhoa.linksentry.analysis.rules.ExcessiveSubdomainsRule;
import com.lyanhkhoa.linksentry.analysis.rules.ExcessiveUrlLengthRule;
import com.lyanhkhoa.linksentry.analysis.rules.IpLiteralHostRule;
import com.lyanhkhoa.linksentry.analysis.rules.KnownUrlShortenerRule;
import com.lyanhkhoa.linksentry.analysis.rules.MissingHttpsRule;
import com.lyanhkhoa.linksentry.analysis.rules.PunycodeHostRule;
import com.lyanhkhoa.linksentry.analysis.rules.SuspiciousKeywordsRule;
import com.lyanhkhoa.linksentry.analysis.scoring.DefaultRiskScorer;
import com.lyanhkhoa.linksentry.analysis.scoring.RiskScorer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A compact end-to-end corpus wired exactly like production ({@code
 * AnalysisConfig}, with the same defaults as {@code application.yml}), but
 * framework-free — plain constructors, no Spring context.
 *
 * <p>Each case asserts the full, deterministic outcome (exact rule ids in order,
 * score, risk level) rather than a single field, so a regression in one rule's
 * points or in the ordering comparator shows up here even if that rule's own unit
 * test still passes in isolation. Several cases also assert that a submitted
 * secret never survives into the result, which is the property the whole product
 * depends on.
 */
class RegressionCorpusTest {

    // Mirrors application.yml's linksentry.rules.* defaults.
    private static final List<String> SUSPICIOUS_KEYWORDS =
            List.of("login", "verify", "secure", "account", "update", "confirm", "signin", "banking", "password",
                    "billing");
    private static final List<String> KNOWN_SHORTENERS =
            List.of("bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly", "adf.ly", "rebrand.ly",
                    "cutt.ly", "shorturl.at", "rb.gy");
    // Mirrors application.yml's linksentry.brands.entries defaults.
    private static final Brand VIETCOMBANK =
            new Brand("vietcombank", "Vietcombank", List.of("vietcombank"), List.of("vietcombank.com.vn"));
    private static final Brand TECHCOMBANK =
            new Brand("techcombank", "Techcombank", List.of("techcombank"), List.of("techcombank.com.vn"));

    private final UrlAnalyzer analyzer = new DefaultUrlAnalyzer(normalizer(), rules(), scorer());

    @Test
    @DisplayName("a clean HTTPS hostname produces no findings and scores LOW")
    void cleanHttpsHostname() {
        AnalysisResult result = analyzer.analyze("https://example.com/");

        assertRuleIds(result);
        assertThat(result.score()).isZero();
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("plain HTTP fires only MISSING_HTTPS and scores LOW")
    void plainHttp() {
        AnalysisResult result = analyzer.analyze("http://example.com/");

        assertRuleIds(result, "MISSING_HTTPS");
        assertThat(result.score()).isEqualTo(5);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("an IPv4 literal host fires IP_LITERAL_HOST ahead of MISSING_HTTPS and scores MODERATE")
    void ipv4Literal() {
        AnalysisResult result = analyzer.analyze("http://203.0.113.5/");

        assertRuleIds(result, "IP_LITERAL_HOST", "MISSING_HTTPS");
        assertThat(result.score()).isEqualTo(20);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MODERATE);
    }

    @Test
    @DisplayName("an IPv6 literal host fires IP_LITERAL_HOST ahead of MISSING_HTTPS and scores MODERATE")
    void ipv6Literal() {
        AnalysisResult result = analyzer.analyze("http://[2001:db8::1]/");

        assertRuleIds(result, "IP_LITERAL_HOST", "MISSING_HTTPS");
        assertThat(result.score()).isEqualTo(20);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MODERATE);
    }

    @Test
    @DisplayName("a multi-label public suffix (bbc.co.uk) is not mistaken for a deep subdomain chain")
    void multiLabelPublicSuffix() {
        AnalysisResult result = analyzer.analyze("https://bbc.co.uk/news");

        assertThat(result.normalizedUrl().registrableDomain()).isEqualTo("bbc.co.uk");
        assertThat(result.normalizedUrl().subdomains()).isEmpty();
        assertRuleIds(result);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("a private suffix (example.github.io) is treated as its own registrable domain")
    void privateSuffix() {
        AnalysisResult result = analyzer.analyze("https://example.github.io/");

        assertThat(result.normalizedUrl().registrableDomain()).isEqualTo("example.github.io");
        assertThat(result.normalizedUrl().subdomains()).isEmpty();
        assertRuleIds(result);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("a deceptive subdomain chain fires BRAND_DOMAIN_MISMATCH, EXCESSIVE_SUBDOMAINS, "
            + "SUSPICIOUS_KEYWORDS, then MISSING_HTTPS")
    void deceptiveSubdomains() {
        AnalysisResult result = analyzer.analyze("http://login.vietcombank.com.vn.evil-domain.xyz/account");

        assertThat(result.normalizedUrl().registrableDomain()).isEqualTo("evil-domain.xyz");
        assertRuleIds(
                result, "BRAND_DOMAIN_MISMATCH", "EXCESSIVE_SUBDOMAINS", "SUSPICIOUS_KEYWORDS", "MISSING_HTTPS");
        assertThat(result.score()).isEqualTo(75);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(result.findings().get(0).evidence()).contains("Vietcombank").contains("vietcombank.com.vn");
    }

    @Test
    @DisplayName("a known brand's own official domain fires no findings and scores LOW")
    void officialBrandDomain() {
        AnalysisResult result = analyzer.analyze("https://vietcombank.com.vn/");

        assertRuleIds(result);
        assertThat(result.score()).isZero();
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("a known brand's subdomain fires no findings and scores LOW")
    void officialBrandSubdomain() {
        AnalysisResult result = analyzer.analyze("https://mobile.vietcombank.com.vn/");

        assertRuleIds(result);
        assertThat(result.score()).isZero();
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("a brand token on an unrelated domain fires only BRAND_DOMAIN_MISMATCH and scores MODERATE")
    void brandTokenOnUnrelatedDomain() {
        AnalysisResult result = analyzer.analyze("https://vietcombank.evil-domain.xyz/");

        assertRuleIds(result, "BRAND_DOMAIN_MISMATCH");
        assertThat(result.score()).isEqualTo(30);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MODERATE);
        assertThat(result.findings().get(0).evidence()).contains("Vietcombank").contains("vietcombank.com.vn");
    }

    @Test
    @DisplayName("multiple configured brand tokens in one hostname deterministically pick the first configured "
            + "brand and fire exactly one finding")
    void multipleConfiguredBrandTokens() {
        AnalysisResult result = analyzer.analyze("https://vietcombank-techcombank.evil-domain.xyz/");

        assertRuleIds(result, "BRAND_DOMAIN_MISMATCH");
        assertThat(result.score()).isEqualTo(30);
        assertThat(result.findings().get(0).evidence()).contains("Vietcombank").doesNotContain("Techcombank");
    }

    @Test
    @DisplayName("a punycode host fires only PUNYCODE_HOST and scores MODERATE")
    void punycodeHost() {
        AnalysisResult result = analyzer.analyze("https://xn--80ak6aa92e.com/");

        assertRuleIds(result, "PUNYCODE_HOST");
        assertThat(result.score()).isEqualTo(15);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MODERATE);
    }

    @Test
    @DisplayName("a known URL shortener fires only KNOWN_URL_SHORTENER and scores MODERATE")
    void knownUrlShortener() {
        AnalysisResult result = analyzer.analyze("https://bit.ly/abc123");

        assertRuleIds(result, "KNOWN_URL_SHORTENER");
        assertThat(result.score()).isEqualTo(10);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MODERATE);
    }

    @Test
    @DisplayName("a percent-encoded path fires only ENCODED_CHARACTERS, and a query secret never survives")
    void percentEncodedPathWithQuerySecret() {
        AnalysisResult result = analyzer.analyze("https://example.com/reset%20password?token=SECRET123");

        assertRuleIds(result, "ENCODED_CHARACTERS");
        assertThat(result.score()).isEqualTo(10);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MODERATE);
        assertNoFindingLeaksSecret(result, "SECRET123");
        assertThat(result.normalizedUrl().redactedDisplayValue()).doesNotContain("SECRET123");
    }

    @Test
    @DisplayName("credentials and query values never survive into the redacted display value")
    void credentialAndQueryRedaction() {
        AnalysisResult result = analyzer.analyze("https://example.com/reset-password?token=SECRET123&pwd=hunter2");

        assertThat(result.normalizedUrl().redactedDisplayValue())
                .isEqualTo("https://example.com/reset-password")
                .doesNotContain("SECRET123")
                .doesNotContain("hunter2");
        assertNoFindingLeaksSecret(result, "SECRET123");
        assertNoFindingLeaksSecret(result, "hunter2");
    }

    @Test
    @DisplayName("a brand mismatch finding never leaks a query secret carried by the same URL")
    void brandMismatchDoesNotLeakQuerySecret() {
        AnalysisResult result =
                analyzer.analyze("http://login.vietcombank.com.vn.evil-domain.xyz/account?token=SECRET123");

        assertThat(result.findings()).extracting(RuleFinding::ruleId).contains("BRAND_DOMAIN_MISMATCH");
        assertNoFindingLeaksSecret(result, "SECRET123");
        assertThat(result.normalizedUrl().redactedDisplayValue()).doesNotContain("SECRET123");
    }

    @Test
    @DisplayName("malformed input is rejected before any rule runs")
    void malformedInput() {
        assertThatExceptionOfType(InvalidUrlException.class).isThrownBy(() -> analyzer.analyze("not a url"));
    }

    @Test
    @DisplayName("an unsupported scheme is rejected before any rule runs")
    void unsupportedScheme() {
        assertThatExceptionOfType(InvalidUrlException.class)
                .isThrownBy(() -> analyzer.analyze("ftp://example.com/file"));
    }

    @Test
    @DisplayName("input over the normalizer's hard length cap is rejected before EXCESSIVE_URL_LENGTH ever runs")
    void overlongInput() {
        String overlong = "https://example.com/" + "a".repeat(UrlNormalizer.MAX_URL_LENGTH);

        assertThatExceptionOfType(InvalidUrlException.class).isThrownBy(() -> analyzer.analyze(overlong));
    }

    private static void assertRuleIds(AnalysisResult result, String... expectedRuleIdsInOrder) {
        assertThat(result.findings()).extracting(RuleFinding::ruleId).containsExactly(expectedRuleIdsInOrder);
    }

    private static void assertNoFindingLeaksSecret(AnalysisResult result, String secret) {
        for (RuleFinding finding : result.findings()) {
            assertThat(finding.title()).doesNotContain(secret);
            assertThat(finding.explanation()).doesNotContain(secret);
            if (finding.evidence() != null) {
                assertThat(finding.evidence()).doesNotContain(secret);
            }
        }
    }

    private static UrlNormalizer normalizer() {
        return new DefaultUrlNormalizer();
    }

    private static RiskScorer scorer() {
        return new DefaultRiskScorer();
    }

    private static List<AnalysisRule> rules() {
        return List.of(
                new MissingHttpsRule(),
                new IpLiteralHostRule(),
                new ExcessiveUrlLengthRule(100),
                new ExcessiveSubdomainsRule(3),
                new SuspiciousKeywordsRule(SUSPICIOUS_KEYWORDS),
                new BrandDomainMismatchRule(new BrandRegistry(List.of(VIETCOMBANK, TECHCOMBANK))),
                new PunycodeHostRule(),
                new EncodedCharactersRule(),
                new KnownUrlShortenerRule(KNOWN_SHORTENERS));
    }
}
