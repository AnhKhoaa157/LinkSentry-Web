package com.lyanhkhoa.linksentry.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisResult;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import com.lyanhkhoa.linksentry.analysis.rules.Brand;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Locks the hand-curated brand registry to version-controlled evidence metadata
 * and a deterministic positive/negative coverage matrix. The source values are
 * documentation only: this test never visits, resolves, or renders them.
 */
class BrandRegistryEvidenceTest {

    private static final String EVIDENCE_RESOURCE = "/brand-registry-evidence.csv";
    private static final String DOMAIN_MISMATCH = "BRAND_DOMAIN_MISMATCH";
    private static final String LOOKALIKE = "BRAND_LOOKALIKE_HOSTNAME";

    private final UrlAnalyzer analyzer = AnalyzerFixture.productionAnalyzer();

    @ParameterizedTest(name = "[{index}] {0} ({1})")
    @CsvFileSource(resources = EVIDENCE_RESOURCE, numLinesToSkip = 1, delimiterString = "|", encoding = "UTF-8")
    @DisplayName("each curated brand has evidence metadata and registry boundary coverage")
    void evidenceRowCoversBrandBoundaries(
            String brandId,
            String displayName,
            String officialDomainsRaw,
            String officialSource,
            String verificationDate,
            String reviewer,
            String negativeCasesRaw,
            String negativeHost) {

        Brand brand = findBrand(brandId);
        List<String> officialDomains = split(officialDomainsRaw, ",");

        assertThat(brand.displayName()).isEqualTo(displayName);
        assertThat(brand.officialDomains()).containsExactlyElementsOf(officialDomains);
        assertOfficialSource(brandId, officialSource, officialDomains);
        assertThat(verificationDate).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(reviewer == null ? "" : reviewer.trim())
                .as(brandId + " reviewer must be a real identifier; REVIEWER_TBD is not verification")
                .isNotBlank()
                .isNotEqualToIgnoringCase("REVIEWER_TBD");
        assertThat(split(negativeCasesRaw, ";"))
                .containsExactlyInAnyOrder("official-apex", "official-subdomain", "path-only", "unrelated-host");

        for (String officialDomain : officialDomains) {
            assertBrandRuleIds(
                    analyzer.analyze("https://" + officialDomain + "/"),
                    brandId + " apex " + officialDomain,
                    List.of());
            assertBrandRuleIds(
                    analyzer.analyze("https://portal." + officialDomain + "/"),
                    brandId + " official subdomain " + officialDomain,
                    List.of());
        }
        assertBrandRuleIds(
                analyzer.analyze("https://" + brand.tokens().get(0) + ".registry-evidence.xyz/"),
                brandId + " mismatch",
                List.of(DOMAIN_MISMATCH));
        assertBrandRuleIds(
                analyzer.analyze("https://example.com/" + brand.id() + "-support?ref=registry#path-only"),
                brandId + " path-only",
                List.of());
        assertBrandRuleIds(
                analyzer.analyze("https://" + negativeHost + "/"),
                brandId + " unrelated negative host",
                List.of());
    }

    @Test
    @DisplayName("official source validation rejects HTTP and domains hidden in query or path")
    void officialSourceValidationRejectsTransportAndEmbeddedDomainSpoofs() {
        List<String> officialDomains = List.of("vietcombank.com.vn");

        assertThatThrownBy(() -> assertOfficialSource(
                        "vietcombank", "http://vietcombank.com.vn/", officialDomains))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> assertOfficialSource(
                        "vietcombank",
                        "https://attacker.example/?next=https://vietcombank.com.vn/",
                        officialDomains))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> assertOfficialSource(
                        "vietcombank", "https://attacker.example/vietcombank.com.vn", officialDomains))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("evidence rows cover exactly the configured curated brands")
    void everyConfiguredBrandHasEvidenceRow() {
        assertThat(readEvidenceIds())
                .containsExactlyInAnyOrderElementsOf(
                        AnalyzerFixture.productionBrandRegistry().brands().stream().map(Brand::id).toList());
    }

    private static Brand findBrand(String brandId) {
        return AnalyzerFixture.productionBrandRegistry().brands().stream()
                .filter(brand -> brand.id().equals(brandId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Evidence resource names an unknown brand: " + brandId));
    }

    private static void assertBrandRuleIds(AnalysisResult result, String caseLabel, List<String> expectedRuleIds) {
        List<String> actualRuleIds = result.findings().stream()
                .map(RuleFinding::ruleId)
                .filter(ruleId -> ruleId.equals(DOMAIN_MISMATCH) || ruleId.equals(LOOKALIKE))
                .toList();
        assertThat(actualRuleIds).as(caseLabel + " brand rule ids").containsExactlyElementsOf(expectedRuleIds);
    }

    private static void assertOfficialSource(String brandId, String officialSource, List<String> officialDomains) {
        URI source;
        try {
            source = URI.create(officialSource);
        } catch (IllegalArgumentException exception) {
            throw new AssertionError(brandId + " official source must be a valid URI", exception);
        }

        String scheme = source.getScheme();
        assertThat(scheme)
                .as(brandId + " official source must use HTTPS")
                .isNotNull();
        assertThat(scheme.toLowerCase(Locale.ROOT))
                .as(brandId + " official source must use HTTPS")
                .isEqualTo("https");

        String sourceHost = source.getHost();
        assertThat(sourceHost)
                .as(brandId + " official source must contain a host")
                .isNotBlank();
        String normalizedSourceHost = sourceHost == null ? "" : sourceHost.toLowerCase(Locale.ROOT);
        assertThat(officialDomains.stream().anyMatch(
                        officialDomain -> isSameOrSubdomain(normalizedSourceHost, officialDomain)))
                .as(brandId + " official source host must be an official domain or subdomain")
                .isTrue();
    }

    private static boolean isSameOrSubdomain(String host, String officialDomain) {
        String normalizedOfficialDomain = officialDomain.toLowerCase(Locale.ROOT);
        return host.equals(normalizedOfficialDomain) || host.endsWith("." + normalizedOfficialDomain);
    }

    private static Set<String> readEvidenceIds() {
        Set<String> ids = new LinkedHashSet<>();
        try (InputStream stream = BrandRegistryEvidenceTest.class.getResourceAsStream(EVIDENCE_RESOURCE)) {
            Objects.requireNonNull(stream, EVIDENCE_RESOURCE + " must be on the test classpath");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        ids.add(line.substring(0, line.indexOf('|')));
                    }
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return ids;
    }

    private static List<String> split(String raw, String delimiter) {
        return Arrays.stream(raw.split(delimiter))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
