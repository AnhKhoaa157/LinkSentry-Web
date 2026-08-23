package com.lyanhkhoa.linksentry.analysis.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.analysis.domain.DomainFeatures;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import com.lyanhkhoa.linksentry.analysis.normalization.DefaultUrlNormalizer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BrandImpersonationApkRuleTest {

    private static final Brand VIETCOMBANK =
            new Brand("vietcombank", "Vietcombank", List.of("vietcombank"), List.of("vietcombank.com.vn"));
    private static final Brand TECHCOMBANK =
            new Brand(
                    "techcombank",
                    "Techcombank",
                    List.of("techcombank"),
                    List.of("techcombank.com.vn", "techcombank.com"));

    private static final BrandRegistry REGISTRY = new BrandRegistry(List.of(VIETCOMBANK, TECHCOMBANK));

    private final BrandImpersonationApkRule rule =
            new BrandImpersonationApkRule(REGISTRY);
    private final DefaultUrlNormalizer normalizer = new DefaultUrlNormalizer();

    @Test
    @DisplayName("an exact mismatched brand plus a literal APK suffix produces one composite finding")
    void exactBrandMismatchWithApkFires() {
        RuleFinding finding = rule.analyze(normalize(
                        "https://vietcombank.example.com/mobile-update.apk?token=M3_QUERY_SECRET#M3_FRAGMENT_SECRET"))
                .orElseThrow();

        assertThat(finding.ruleId()).isEqualTo(BrandImpersonationApkRule.RULE_ID);
        assertThat(finding.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(finding.points()).isEqualTo(20);
        assertThat(finding.evidence())
                .contains("Vietcombank", "vietcombank.com.vn", "Android package download")
                .doesNotContain(
                        "vietcombank.example.com",
                        "/mobile-update.apk",
                        "M3_QUERY_SECRET",
                        "M3_FRAGMENT_SECRET");
    }

    @Test
    @DisplayName("a bounded one-character lookalike plus a literal APK suffix fires")
    void boundedTypoWithApkFires() {
        assertThat(rule.analyze(normalize("https://vietcomban.example.com/mobile.apk"))).isPresent();
    }

    @Test
    @DisplayName("a bounded hyphen lookalike plus a literal APK suffix fires")
    void boundedHyphenLookalikeWithApkFires() {
        assertThat(rule.analyze(normalize("https://viet-com-bank.example.com/mobile.apk"))).isPresent();
    }

    @Test
    @DisplayName("the literal suffix comparison is case-insensitive")
    void uppercaseApkSuffixFires() {
        assertThat(rule.analyze(normalize("https://vietcombank.example.com/mobile.APK"))).isPresent();
    }

    @Test
    @DisplayName("an official brand domain with an APK does not produce the composite finding")
    void officialBrandWithApkDoesNotFire() {
        assertThat(rule.analyze(normalize("https://mobile.vietcombank.com.vn/downloads/official.apk"))).isEmpty();
    }

    @Test
    @DisplayName("an arbitrary APK without a curated brand match does not fire")
    void arbitraryApkDoesNotFire() {
        assertThat(rule.analyze(normalize("https://downloads.example/installer.apk"))).isEmpty();
    }

    @Test
    @DisplayName("a brand mismatch without an APK suffix does not fire")
    void brandMismatchWithoutApkDoesNotFire() {
        assertThat(rule.analyze(normalize("https://vietcombank.example.com/account"))).isEmpty();
    }

    @Test
    @DisplayName("an encoded APK suffix does not fire because the rule never decodes path text")
    void encodedApkSuffixDoesNotFire() {
        assertThat(rule.analyze(normalize("https://vietcombank.example.com/mobile-update%2Eapk"))).isEmpty();
    }

    @Test
    @DisplayName("a brand name only in the path does not fire")
    void brandOnlyInPathDoesNotFire() {
        assertThat(rule.analyze(normalize("https://downloads.example.com/vietcombank-mobile.apk"))).isEmpty();
    }

    @Test
    @DisplayName("an APK directory path does not fire because the final path segment is not a filename")
    void apkDirectoryDoesNotFire() {
        assertThat(rule.analyze(normalize("https://vietcombank.example.com/mobile.apk/"))).isEmpty();
    }

    @Test
    @DisplayName("IP literals and hosts without a registrable domain do not fire")
    void ipLiteralAndMissingRegistrableDomainDoNotFire() {
        assertThat(rule.analyze(normalize("https://203.0.113.5/mobile.apk"))).isEmpty();

        NormalizedUrl noRegistrableDomain = directUrl(
                "vietcombank.example", null, "/mobile.apk", false);
        assertThat(rule.analyze(noRegistrableDomain)).isEmpty();
    }

    private NormalizedUrl normalize(String raw) {
        return normalizer.normalize(raw);
    }

    private static NormalizedUrl directUrl(
            String asciiHost, String registrableDomain, String path, boolean ipLiteral) {
        String raw = "https://" + asciiHost + path;
        return new NormalizedUrl(
                raw,
                raw,
                "https",
                asciiHost,
                asciiHost,
                DomainFeatures.fromAsciiHost(asciiHost),
                registrableDomain,
                List.of(),
                null,
                path,
                false,
                false,
                ipLiteral);
    }
}
