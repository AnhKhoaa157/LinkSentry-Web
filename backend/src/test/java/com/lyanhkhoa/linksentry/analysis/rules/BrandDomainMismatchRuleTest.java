package com.lyanhkhoa.linksentry.analysis.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.analysis.domain.DomainFeatures;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BrandDomainMismatchRuleTest {

    private static final Brand VIETCOMBANK =
            new Brand("vietcombank", "Vietcombank", List.of("vietcombank"), List.of("vietcombank.com.vn"));
    private static final Brand TECHCOMBANK =
            new Brand("techcombank", "Techcombank", List.of("techcombank"), List.of("techcombank.com.vn"));

    private final BrandDomainMismatchRule rule =
            new BrandDomainMismatchRule(new BrandRegistry(List.of(VIETCOMBANK, TECHCOMBANK)));

    @Test
    @DisplayName("the official domain itself produces no finding")
    void officialDomainProducesNoFinding() {
        NormalizedUrl url = urlFor("vietcombank.com.vn", "vietcombank.com.vn", List.of(), "/");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("a subdomain of the official domain produces no finding")
    void subdomainOfOfficialDomainProducesNoFinding() {
        NormalizedUrl url =
                urlFor("mobile.vietcombank.com.vn", "vietcombank.com.vn", List.of("mobile"), "/");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("the brand name buried ahead of an unrelated registrable domain produces a finding")
    void brandTokenWithMismatchedDomainProducesFinding() {
        NormalizedUrl url = urlFor(
                "login.vietcombank.com.vn.evil-domain.xyz",
                "evil-domain.xyz",
                List.of("login", "vietcombank", "com", "vn"),
                "/account");

        var finding = rule.analyze(url);

        assertThat(finding).isPresent();
        assertThat(finding.get().ruleId()).isEqualTo(BrandDomainMismatchRule.RULE_ID);
        assertThat(finding.get().evidence()).contains("Vietcombank").contains("vietcombank.com.vn");
    }

    @Test
    @DisplayName("the brand token inside a hyphenated hostile hostname produces a finding")
    void hyphenatedBrandTokenProducesFinding() {
        NormalizedUrl url = urlFor(
                "vietcombank-secure-login.xyz", "vietcombank-secure-login.xyz", List.of(), "/");

        assertThat(rule.analyze(url)).isPresent();
    }

    @Test
    @DisplayName("the brand name only in the path produces no finding")
    void brandNameOnlyInPathProducesNoFinding() {
        NormalizedUrl url = urlFor("example.com", "example.com", List.of(), "/vietcombank/login");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("an unrelated domain produces no finding")
    void unrelatedDomainProducesNoFinding() {
        NormalizedUrl url = urlFor("example.com", "example.com", List.of(), "/");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("an IP literal host produces no finding")
    void ipLiteralHostProducesNoFinding() {
        String raw = "https://203.0.113.5/vietcombank";
        NormalizedUrl url = new NormalizedUrl(
                raw, raw, "https", "203.0.113.5", "203.0.113.5", DomainFeatures.fromAsciiHost("203.0.113.5"), null,
                List.of(), null, "/vietcombank", false, false, true);

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("when multiple configured brands match, the first configured brand wins deterministically")
    void multipleBrandMatchesPickFirstConfiguredBrand() {
        NormalizedUrl url = urlFor(
                "vietcombank-techcombank.evil.example",
                "evil.example",
                List.of(),
                "/");

        var finding = rule.analyze(url);

        assertThat(finding).isPresent();
        assertThat(finding.get().evidence()).contains("Vietcombank").doesNotContain("Techcombank");
    }

    private static NormalizedUrl urlFor(
            String asciiHost, String registrableDomain, List<String> subdomains, String path) {
        String raw = "https://" + asciiHost + path;
        return new NormalizedUrl(
                raw, raw, "https", asciiHost, asciiHost, DomainFeatures.fromAsciiHost(asciiHost), registrableDomain,
                subdomains, null, path, false, false, false);
    }
}
