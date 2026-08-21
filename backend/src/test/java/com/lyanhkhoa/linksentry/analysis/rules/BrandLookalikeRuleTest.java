package com.lyanhkhoa.linksentry.analysis.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.analysis.domain.DomainFeatures;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.normalization.IdnaProcessor;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BrandLookalikeRuleTest {

    private static final IdnaProcessor IDNA_PROCESSOR = new IdnaProcessor();

    private static final Brand VIETCOMBANK =
            new Brand("vietcombank", "Vietcombank", List.of("vietcombank"), List.of("vietcombank.com.vn"));
    private static final Brand TECHCOMBANK =
            new Brand("techcombank", "Techcombank", List.of("techcombank"), List.of("techcombank.com.vn"));
    private static final Brand ACB = new Brand("acb", "ACB", List.of("acb"), List.of("acb.com.vn"));

    private final BrandLookalikeRule rule =
            new BrandLookalikeRule(new BrandRegistry(List.of(VIETCOMBANK, TECHCOMBANK, ACB)));

    @Test
    @DisplayName("a one-character substitution typo of a long token fires with the typo signal")
    void oneCharacterSubstitutionFires() {
        NormalizedUrl url = urlFor("v1etcombank.xyz", "v1etcombank.xyz", List.of(), "/");

        var finding = rule.analyze(url);

        assertThat(finding).isPresent();
        assertThat(finding.get().ruleId()).isEqualTo(BrandLookalikeRule.RULE_ID);
        assertThat(finding.get().evidence())
                .contains("Vietcombank")
                .contains("vietcombank.com.vn")
                .contains("one-character typo");
    }

    @Test
    @DisplayName("a one-character deletion typo fires with the typo signal")
    void oneCharacterDeletionFires() {
        NormalizedUrl url = urlFor("vietcombnk.xyz", "vietcombnk.xyz", List.of(), "/");

        assertThat(rule.analyze(url)).isPresent();
    }

    @Test
    @DisplayName("a one-character insertion typo fires with the typo signal")
    void oneCharacterInsertionFires() {
        NormalizedUrl url = urlFor("vietcombanks.xyz", "vietcombanks.xyz", List.of(), "/");

        assertThat(rule.analyze(url)).isPresent();
    }

    @Test
    @DisplayName("an adjacent-character transposition typo fires with the typo signal")
    void transpositionFires() {
        NormalizedUrl url = urlFor("vietcombakn.xyz", "vietcombakn.xyz", List.of(), "/");

        assertThat(rule.analyze(url)).isPresent();
    }

    @Test
    @DisplayName("a one-character edit on a short token (below length 5) never fires")
    void shortTokenTypoNeverFires() {
        NormalizedUrl url = urlFor("acbb.xyz", "acbb.xyz", List.of(), "/");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("a hyphen-separated label that collapses exactly to a token fires with the hyphen signal")
    void hyphenCollapseFires() {
        NormalizedUrl url = urlFor("viet-com-bank.xyz", "viet-com-bank.xyz", List.of(), "/");

        var finding = rule.analyze(url);

        assertThat(finding).isPresent();
        assertThat(finding.get().evidence()).contains("hyphen-separated obfuscation");
    }

    @Test
    @DisplayName("a hyphen-separated label that does NOT collapse to a token never fires")
    void hyphenCollapseMismatchNeverFires() {
        NormalizedUrl url = urlFor("viet-combankz.xyz", "viet-combankz.xyz", List.of(), "/");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("a short token (below length 5) split by hyphens never fires, even when it collapses exactly")
    void shortTokenHyphenCollapseNeverFires() {
        NormalizedUrl url = urlFor("a-c-b.xyz", "a-c-b.xyz", List.of(), "/");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("a Cyrillic confusable substitution that decodes exactly to a token fires with the lookalike signal")
    void confusableCharacterFires() {
        String confusableLabel = "vietc" + 'о' + "mbank"; // Cyrillic о (U+043E) in place of 'o'
        String asciiHost = IDNA_PROCESSOR.toAscii(confusableLabel) + ".xyz";
        NormalizedUrl url = urlFor(asciiHost, asciiHost, List.of(), "/");

        var finding = rule.analyze(url);

        assertThat(finding).isPresent();
        assertThat(finding.get().evidence()).contains("lookalike character");
    }

    @Test
    @DisplayName("an uppercase XN-- Punycode prefix is still recognized and fires with the lookalike signal")
    void uppercasePunycodePrefixFires() {
        String confusableLabel = "vietc" + 'о' + "mbank"; // Cyrillic о (U+043E) in place of 'o'
        String encodedLabel = IDNA_PROCESSOR.toAscii(confusableLabel);
        String uppercasePrefixLabel = "XN--" + encodedLabel.substring(4);
        String asciiHost = uppercasePrefixLabel + ".xyz";
        NormalizedUrl url = urlFor(asciiHost, asciiHost, List.of(), "/");

        var finding = rule.analyze(url);

        assertThat(finding).isPresent();
        assertThat(finding.get().evidence()).contains("lookalike character");
    }

    @Test
    @DisplayName("an unrelated internationalized hostname with no confusable mapping to a token never fires")
    void unrelatedIdnHostNeverFires() {
        String label = "münchen"; // German umlaut, decodes cleanly but matches no brand token
        String asciiHost = IDNA_PROCESSOR.toAscii(label) + ".example";
        NormalizedUrl url = urlFor(asciiHost, asciiHost, List.of(), "/");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("the official domain itself never fires")
    void officialDomainNeverFires() {
        NormalizedUrl url = urlFor("vietcombank.com.vn", "vietcombank.com.vn", List.of(), "/");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("a subdomain of the official domain never fires")
    void officialSubdomainNeverFires() {
        NormalizedUrl url = urlFor("mobile.vietcombank.com.vn", "vietcombank.com.vn", List.of("mobile"), "/");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("an exact brand token already identified by exact matching is not also flagged as a lookalike")
    void exactMatchIsNotDoubleFlagged() {
        NormalizedUrl url = urlFor("vietcombank.evil.example", "evil.example", List.of(), "/");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("a distant spelling never fires")
    void distantSpellingNeverFires() {
        NormalizedUrl url = urlFor("vcombk.xyz", "vcombk.xyz", List.of(), "/");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("generic words and an unrelated host never fire")
    void unrelatedHostNeverFires() {
        NormalizedUrl url = urlFor("example.com", "example.com", List.of(), "/");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("innocent text containing a brand-like substring in the path never fires")
    void brandLikeSubstringInPathNeverFires() {
        NormalizedUrl url = urlFor("example.com", "example.com", List.of(), "/vietcombank-review");

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("an IP literal host never fires")
    void ipLiteralHostNeverFires() {
        String raw = "https://203.0.113.5/vietcombank";
        NormalizedUrl url = new NormalizedUrl(
                raw, raw, "https", "203.0.113.5", "203.0.113.5", DomainFeatures.fromAsciiHost("203.0.113.5"), null,
                List.of(), null, "/vietcombank", false, false, true);

        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("when multiple brands could match, the first configured brand wins deterministically")
    void multipleBrandMatchesPickFirstConfiguredBrand() {
        NormalizedUrl url = urlFor("v1etcombank.techc0mbank.evil.example", "evil.example", List.of(), "/");

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
