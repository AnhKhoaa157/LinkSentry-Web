package com.lyanhkhoa.linksentry.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisResult;
import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import com.lyanhkhoa.linksentry.analysis.normalization.IdnaProcessor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercises the approved ICU4J UTS #46 contract from the checked-in corpus.
 * The corpus stores Unicode escape sequences literally so it remains portable
 * across editors and is decoded here without a file, network, or runtime lookup.
 */
class IdnaRegressionCorpusTest {

    private static final String CORPUS_RESOURCE = "/idna-uts46-regression-corpus.csv";
    private static final Set<String> REQUIRED_CATEGORIES = Set.of(
            "benign-idn",
            "valid-punycode",
            "malformed-label",
            "bidi",
            "contextj",
            "contexto-policy",
            "disallowed-code-point",
            "mixed-script",
            "confusable-brand");
    private static final Set<String> SEPARATE_ANALYSIS_RULES =
            Set.of("PUNYCODE_HOST", "BRAND_LOOKALIKE_HOSTNAME");

    private final IdnaProcessor processor = new IdnaProcessor();
    private final UrlAnalyzer analyzer = AnalyzerFixture.productionAnalyzer();

    @Test
    @DisplayName("the approved UTS #46 corpus pins accepted ASCII output and rejection behavior")
    void corpusPinsApprovedIdnaBehavior() {
        List<CorpusCase> cases = readCorpus();

        assertThat(cases).hasSize(37);
        assertThat(cases.stream().map(CorpusCase::category).collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(REQUIRED_CATEGORIES);

        for (CorpusCase testCase : cases) {
            String host = decodeUnicodeEscapes(testCase.sourceHost());
            if ("NONE".equals(testCase.expectedFailureBehavior())) {
                assertThat(processor.toAscii(host))
                        .as(testCase.id())
                        .isEqualTo(testCase.expectedAscii());
            } else {
                assertThatThrownBy(() -> processor.toAscii(host))
                        .as(testCase.id())
                        .isExactlyInstanceOf(InvalidUrlException.class)
                        .hasMessage("URL must have a valid host")
                        .hasNoCause();
            }
        }
    }

    @Test
    @DisplayName("accepted mixed-script and brand rows remain separate from IDNA validity")
    void acceptedMixedScriptAndBrandRowsUseSeparateBoundedAnalysis() {
        for (CorpusCase testCase : readCorpus()) {
            if (!Set.of("mixed-script", "confusable-brand").contains(testCase.category())) {
                continue;
            }

            String asciiHost = processor.toAscii(decodeUnicodeEscapes(testCase.sourceHost()));
            AnalysisResult result = analyzer.analyze("https://" + asciiHost + "/");
            Set<String> ruleIds = result.findings().stream()
                    .map(RuleFinding::ruleId)
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(ruleIds).as(testCase.id()).allMatch(SEPARATE_ANALYSIS_RULES::contains);
            assertThat(result.normalizedUrl().asciiHost()).as(testCase.id()).isEqualTo(asciiHost);
        }
    }

    @Test
    @DisplayName("generic IDNA rejection never exposes host text or ICU metadata")
    void genericRejectionDoesNotExposeInputOrIcuErrors() {
        String hostText = "xn--0.example?token=IDNA_SECRET#fragment";

        assertThatThrownBy(() -> processor.toAscii(hostText))
                .isExactlyInstanceOf(InvalidUrlException.class)
                .hasMessage("URL must have a valid host")
                .hasMessageNotContaining("xn--0")
                .hasMessageNotContaining("IDNA_SECRET")
                .hasMessageNotContaining("INVALID_ACE_LABEL")
                .hasNoCause();
    }

    private static List<CorpusCase> readCorpus() {
        List<CorpusCase> cases = new ArrayList<>();
        try (InputStream stream = IdnaRegressionCorpusTest.class.getResourceAsStream(CORPUS_RESOURCE)) {
            Objects.requireNonNull(stream, CORPUS_RESOURCE + " must be on the test classpath");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String header = reader.readLine();
                assertThat(header).isEqualTo(
                        "id|category|source_host|expected_uts46_ascii|intended_classification|"
                                + "expected_failure_behavior|security_interpretation|reference_key");

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] fields = line.split("\\|", -1);
                    if (fields.length != 8) {
                        throw new AssertionError("IDNA corpus row must have eight fields");
                    }
                    cases.add(new CorpusCase(
                            fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], fields[6], fields[7]));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return List.copyOf(cases);
    }

    private static String decodeUnicodeEscapes(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '\\'
                    && index + 5 < value.length()
                    && value.charAt(index + 1) == 'u') {
                int codePoint = 0;
                boolean validEscape = true;
                for (int offset = 2; offset <= 5; offset++) {
                    int digit = hexValue(value.charAt(index + offset));
                    if (digit < 0) {
                        validEscape = false;
                        break;
                    }
                    codePoint = (codePoint << 4) | digit;
                }
                if (validEscape) {
                    decoded.append((char) codePoint);
                    index += 5;
                    continue;
                }
            }
            decoded.append(value.charAt(index));
        }
        return decoded.toString();
    }

    private static int hexValue(char value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        return -1;
    }

    private record CorpusCase(
            String id,
            String category,
            String sourceHost,
            String expectedAscii,
            String intendedClassification,
            String expectedFailureBehavior,
            String securityInterpretation,
            String referenceKey) {
    }
}
