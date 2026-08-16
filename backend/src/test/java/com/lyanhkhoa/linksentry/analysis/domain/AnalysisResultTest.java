package com.lyanhkhoa.linksentry.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Covers the invariants {@link AnalysisResult} enforces today. */
class AnalysisResultTest {

    private static final NormalizedUrl URL = new NormalizedUrl(
            "https://example.com/a",
            "https://example.com/a",
            "https",
            "example.com",
            "example.com",
            "example.com",
            List.of(),
            null,
            "/a",
            false,
            false,
            false);

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 50, 99, 100})
    @DisplayName("accepts any score within 0..100")
    void acceptsScoresInRange(int score) {
        assertThat(new AnalysisResult(URL, List.of(), score, RiskLevel.LOW).score()).isEqualTo(score);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101, Integer.MIN_VALUE, Integer.MAX_VALUE})
    @DisplayName("rejects a score outside 0..100 — clamping is the scorer's job, not a silent fix here")
    void rejectsScoresOutOfRange(int score) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new AnalysisResult(URL, List.of(), score, RiskLevel.LOW))
                .withMessageContaining("score");
    }

    @Test
    @DisplayName("findings are defensively copied so no rule can mutate a shared result")
    void findingsAreDefensivelyCopied() {
        List<RuleFinding> mutable = new ArrayList<>();
        mutable.add(RuleFinding.of("MISSING_HTTPS", Severity.MEDIUM, 15, "Title", "Explanation."));

        AnalysisResult result = new AnalysisResult(URL, mutable, 15, RiskLevel.MODERATE);
        mutable.clear();

        assertThat(result.findings()).hasSize(1);
    }

    @Test
    @DisplayName("an empty finding list is valid and means 'nothing detected', not 'safe'")
    void emptyFindingsAreValid() {
        AnalysisResult result = new AnalysisResult(URL, null, 0, RiskLevel.LOW);

        assertThat(result.findings()).isEmpty();
        assertThat(result.hasNoFindings()).isTrue();
    }
}
