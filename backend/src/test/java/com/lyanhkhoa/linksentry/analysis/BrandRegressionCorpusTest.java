package com.lyanhkhoa.linksentry.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisResult;
import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import com.lyanhkhoa.linksentry.analysis.rules.Brand;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * A data-driven regression corpus for the curated brand registry — every
 * configured {@link Brand}, both brand rules ({@code BRAND_DOMAIN_MISMATCH} and
 * {@code BRAND_LOOKALIKE_HOSTNAME}), and their false-positive guards, exercised
 * end to end through {@link AnalyzerFixture#productionAnalyzer()}.
 *
 * <p>Cases live in {@code src/test/resources/brand-regression-corpus.csv}, one row
 * per case, so a new brand, typo, or confusable case is a data change rather than
 * a copy-pasted test method. Columns:
 *
 * <ol>
 *   <li>{@code id} — unique, descriptive case identifier.
 *   <li>{@code category} — grouping label (see the categories asserted by {@link
 *       #everyConfiguredBrandHasBaselineCoverage()}).
 *   <li>{@code url} — the exact URL submitted to the analyzer. Kept synthetic and
 *       harmless: every host is either an official brand domain, a fictitious
 *       {@code *.xyz}/{@code *.info} domain, an RFC 5737 documentation IP literal
 *       (203.0.113.0/24), or the reserved {@code .example} TLD. Nothing is ever
 *       fetched, resolved, or rendered — the analyzer is a pure function over this
 *       string.
 *   <li>{@code expectRejected} — {@code true} when the URL must be rejected with
 *       {@link InvalidUrlException} before any rule runs (the embedded-credential
 *       case); every other column is ignored for such a row.
 *   <li>{@code expectedRuleIds} — semicolon-separated, in the exact order {@link
 *       AnalysisResult#findings()} must report them (points descending, rule id
 *       ascending on ties). Empty means no findings at all.
 *   <li>{@code expectedScore}, {@code expectedRiskLevel} — the analyzer's full,
 *       deterministic output for the case.
 *   <li>{@code secretSentinels} — semicolon-separated substrings (query values,
 *       fragments, credentials) that must never appear in the redacted display
 *       value or in any finding's title, explanation, or evidence.
 *   <li>{@code evidenceContains} — semicolon-separated substrings that must all
 *       appear somewhere across the findings' evidence text — the positive half of
 *       the evidence-safety check (the brand's display name and official domain
 *       are expected to appear; the raw submitted hostname never is, which every
 *       row's assertion of the finding count and score already constrains, since
 *       the rule implementations never place the hostname in evidence at all).
 * </ol>
 */
class BrandRegressionCorpusTest {

    private static final String CORPUS_RESOURCE = "/brand-regression-corpus.csv";

    private final UrlAnalyzer analyzer = AnalyzerFixture.productionAnalyzer();

    @ParameterizedTest(name = "[{index}] {0} ({1})")
    @CsvFileSource(resources = CORPUS_RESOURCE, numLinesToSkip = 1, delimiterString = "|", encoding = "UTF-8")
    @DisplayName("brand corpus case")
    void corpusCase(
            String id,
            String category,
            String url,
            boolean expectRejected,
            String expectedRuleIdsRaw,
            String expectedScoreRaw,
            String expectedRiskLevelRaw,
            String secretSentinelsRaw,
            String evidenceContainsRaw) {

        String caseLabel = id + " (" + category + ")";

        if (expectRejected) {
            assertThatExceptionOfType(InvalidUrlException.class)
                    .as(caseLabel + " must be rejected before any rule runs")
                    .isThrownBy(() -> analyzer.analyze(url));
            return;
        }

        AnalysisResult result = analyzer.analyze(url);

        assertThat(result.findings())
                .as(caseLabel + " rule ids, in order")
                .extracting(RuleFinding::ruleId)
                .containsExactlyElementsOf(splitOrEmpty(expectedRuleIdsRaw));
        assertThat(result.score()).as(caseLabel + " score").isEqualTo(Integer.parseInt(expectedScoreRaw.trim()));
        assertThat(result.riskLevel())
                .as(caseLabel + " risk level")
                .isEqualTo(RiskLevel.valueOf(expectedRiskLevelRaw.trim()));

        for (String sentinel : splitOrEmpty(secretSentinelsRaw)) {
            assertSentinelNeverLeaks(result, sentinel, caseLabel);
        }

        List<String> requiredEvidence = splitOrEmpty(evidenceContainsRaw);
        if (!requiredEvidence.isEmpty()) {
            String allEvidence = result.findings().stream()
                    .map(RuleFinding::evidence)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" | "));
            for (String required : requiredEvidence) {
                assertThat(allEvidence)
                        .as(caseLabel + " evidence must mention '" + required + "'")
                        .contains(required);
            }
        }
    }

    /**
     * Fails clearly, naming the missing brand, when {@link
     * AnalyzerFixture#productionBrandRegistry()} gains a brand this corpus was
     * never updated for. Each configured brand id must own at least the four
     * baseline case ids: {@code <id>-apex}, {@code <id>-subdomain}, {@code
     * <id>-exact-mismatch}, {@code <id>-path-query-fragment}.
     */
    @Test
    @DisplayName("every configured brand has baseline apex/subdomain/mismatch/path-query-fragment coverage")
    void everyConfiguredBrandHasBaselineCoverage() {
        Set<String> corpusIds = readCorpusIds();
        List<String> missing = new ArrayList<>();

        for (Brand brand : AnalyzerFixture.productionBrandRegistry().brands()) {
            for (String suffix : List.of("-apex", "-subdomain", "-exact-mismatch", "-path-query-fragment")) {
                String expectedCaseId = brand.id() + suffix;
                if (!corpusIds.contains(expectedCaseId)) {
                    missing.add(expectedCaseId);
                }
            }
        }

        assertThat(missing)
                .as("brand-regression-corpus.csv is missing baseline case id(s) for a configured brand — "
                        + "add them before this registry entry can ship")
                .isEmpty();
    }

    private static Set<String> readCorpusIds() {
        Set<String> ids = new LinkedHashSet<>();
        try (InputStream stream = BrandRegressionCorpusTest.class.getResourceAsStream(CORPUS_RESOURCE)) {
            Objects.requireNonNull(stream, CORPUS_RESOURCE + " must be on the test classpath");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line = reader.readLine(); // header
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    ids.add(line.substring(0, line.indexOf('|')));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ids;
    }

    private static List<String> splitOrEmpty(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(";")).map(String::trim).filter(part -> !part.isEmpty()).toList();
    }

    private static void assertSentinelNeverLeaks(AnalysisResult result, String secret, String caseLabel) {
        assertThat(result.normalizedUrl().redactedDisplayValue())
                .as(caseLabel + " redacted display value must never contain '" + secret + "'")
                .doesNotContain(secret);
        for (RuleFinding finding : result.findings()) {
            assertThat(finding.title()).as(caseLabel + " finding title").doesNotContain(secret);
            assertThat(finding.explanation()).as(caseLabel + " finding explanation").doesNotContain(secret);
            if (finding.evidence() != null) {
                assertThat(finding.evidence()).as(caseLabel + " finding evidence").doesNotContain(secret);
            }
        }
    }
}
