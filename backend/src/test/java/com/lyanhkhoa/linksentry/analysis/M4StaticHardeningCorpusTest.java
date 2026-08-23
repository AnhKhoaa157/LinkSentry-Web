package com.lyanhkhoa.linksentry.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisResult;
import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Versioned M4 evidence for static hardening decisions and parser differentials.
 *
 * <p>Parser fixtures use symbolic whitespace/control tokens so a failure names
 * only its stable case id, never the submitted string. Deferred candidates stay
 * documented with explicit evidence gaps and score deltas rather than becoming
 * speculative production heuristics.
 */
class M4StaticHardeningCorpusTest {

    private static final String DECISION_RESOURCE = "/m4-static-hardening-decision-corpus-v1.csv";
    private static final String PARSER_RESOURCE = "/m4-parser-differential-corpus-v1.csv";

    private final UrlAnalyzer analyzer = AnalyzerFixture.productionAnalyzer();

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = DECISION_RESOURCE, numLinesToSkip = 1, delimiterString = "|", encoding = "UTF-8")
    @DisplayName("each M4 candidate has evidence, fixtures, score impact, redaction proof, and a decision")
    void decisionRowIsComplete(
            String version,
            String candidate,
            String evidenceOrReason,
            String positiveFixture,
            String negativeBoundaryFixture,
            String scoreImpact,
            String redactionProof,
            String decision) {

        assertThat(version).isEqualTo("m4-v1");
        assertThat(candidate).isNotBlank();
        assertThat(evidenceOrReason).isNotBlank();
        assertFixtureIds(candidate, positiveFixture);
        assertFixtureIds(candidate, negativeBoundaryFixture);
        assertThat(scoreImpact).isNotBlank();
        assertThat(redactionProof).isNotBlank();
        assertThat(decision).isIn("IMPLEMENT", "DEFER", "REJECT");
    }

    @Test
    @DisplayName("M4 decision artifact names every evaluated candidate exactly once")
    void decisionArtifactPinsCandidateDecisions() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("parser-differential", "IMPLEMENT");
        expected.put("digit-substitution-long-brand", "DEFER");
        expected.put("uts39-full-skeleton", "DEFER");
        expected.put("android-package-secondary-suffixes", "DEFER");
        expected.put("forbidden-static-expansions", "REJECT");

        List<String[]> rows = readRows(DECISION_RESOURCE, 8);
        assertThat(rows).hasSize(expected.size());
        assertThat(readDecisionMap(rows)).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = PARSER_RESOURCE, numLinesToSkip = 1, delimiterString = "|", encoding = "UTF-8")
    @DisplayName("parser differential cases canonicalize or reject before rules")
    void parserCase(
            String id,
            String category,
            String inputEncoding,
            String expectedOutcome,
            String expectedDisplay,
            String expectedHost,
            String expectedPort,
            String expectedPath,
            String expectedRuleIds,
            String expectedScore,
            String expectedRiskLevel,
            String redactionSentinels) {

        String input = decodeFixture(inputEncoding);
        if ("REJECT".equals(expectedOutcome)) {
            assertThatThrownBy(() -> analyzer.analyze(input))
                    .as(id + " (" + category + ") must be rejected before rule execution")
                    .isInstanceOf(InvalidUrlException.class);
            return;
        }

        assertThat(expectedOutcome).as(id + " outcome").isEqualTo("ACCEPT");
        AnalysisResult result = analyzer.analyze(input);

        assertThat(result.normalizedUrl().redactedDisplayValue()).as(id + " canonical display")
                .isEqualTo(expectedDisplay);
        assertThat(result.normalizedUrl().host()).as(id + " normalized host").isEqualTo(expectedHost);
        assertThat(result.normalizedUrl().port()).as(id + " normalized port")
                .isEqualTo(parseOptionalPort(expectedPort));
        assertThat(result.normalizedUrl().path()).as(id + " normalized path").isEqualTo(expectedPath);
        assertThat(result.findings()).as(id + " rule ids")
                .extracting(RuleFinding::ruleId)
                .containsExactlyElementsOf(splitOrEmpty(expectedRuleIds));
        assertThat(result.score()).as(id + " score").isEqualTo(Integer.parseInt(expectedScore));
        assertThat(result.riskLevel()).as(id + " risk level")
                .isEqualTo(RiskLevel.valueOf(expectedRiskLevel));

        for (String sentinel : splitOrEmpty(redactionSentinels)) {
            assertThat(result.normalizedUrl().redactedDisplayValue())
                    .as(id + " redacted display")
                    .doesNotContain(sentinel);
            for (RuleFinding finding : result.findings()) {
                assertThat(finding.title()).as(id + " finding title").doesNotContain(sentinel);
                assertThat(finding.explanation()).as(id + " finding explanation").doesNotContain(sentinel);
                if (finding.evidence() != null) {
                    assertThat(finding.evidence()).as(id + " finding evidence").doesNotContain(sentinel);
                }
            }
        }
    }

    private static void assertFixtureIds(String candidate, String rawFixtureIds) {
        assertThat(splitOrEmpty(rawFixtureIds))
                .as(candidate + " fixture ids")
                .isNotEmpty()
                .allSatisfy(fixture -> assertThat(fixture).matches("[a-z0-9-]+"));
    }

    private static Integer parseOptionalPort(String rawPort) {
        return "-".equals(rawPort) ? null : Integer.valueOf(rawPort);
    }

    private static String decodeFixture(String encoded) {
        return encoded
                .replace("<SP>", " ")
                .replace("<TAB>", "\t")
                .replace("<CR>", "\r")
                .replace("<LF>", "\n")
                .replace("<NUL>", String.valueOf((char) 0))
                .replace("<US>", String.valueOf((char) 31));
    }

    private static List<String> splitOrEmpty(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(";"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static Map<String, String> readDecisionMap(List<String[]> rows) {
        Map<String, String> decisions = new LinkedHashMap<>();
        for (String[] row : rows) {
            decisions.put(row[1], row[7]);
        }
        return decisions;
    }

    private static List<String[]> readRows(String resource, int expectedColumnCount) {
        List<String[]> rows = new ArrayList<>();
        try (InputStream stream = M4StaticHardeningCorpusTest.class.getResourceAsStream(resource)) {
            Objects.requireNonNull(stream, resource + " must be on the test classpath");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                reader.readLine();
                String line;
                int rowNumber = 1;
                while ((line = reader.readLine()) != null) {
                    rowNumber++;
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] row = line.split("\\|", -1);
                    if (row.length != expectedColumnCount) {
                        throw new AssertionError(resource + " row " + rowNumber + " has an unexpected column count");
                    }
                    rows.add(row);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return rows;
    }
}
