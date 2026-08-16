package com.lyanhkhoa.linksentry.analysis.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultRiskScorerTest {

    private final DefaultRiskScorer scorer = new DefaultRiskScorer();

    @Test
    @DisplayName("an empty finding list scores zero")
    void emptyFindingsScoreZero() {
        assertThat(scorer.score(List.of())).isZero();
    }

    @Test
    @DisplayName("score sums every finding's points")
    void scoreSumsPoints() {
        List<RuleFinding> findings = List.of(finding("A", 10), finding("B", 25));

        assertThat(scorer.score(findings)).isEqualTo(35);
    }

    @Test
    @DisplayName("score is clamped at the maximum even when points exceed it")
    void scoreIsClampedAtMaximum() {
        List<RuleFinding> findings = List.of(finding("A", 60), finding("B", 60));

        assertThat(scorer.score(findings)).isEqualTo(100);
    }

    @Test
    @DisplayName("score clamps without overflowing when individual points are very large")
    void scoreDoesNotOverflowBeforeClamping() {
        List<RuleFinding> findings = List.of(
                finding("A", Integer.MAX_VALUE), finding("B", Integer.MAX_VALUE));

        assertThat(scorer.score(findings)).isEqualTo(100);
    }

    @Test
    @DisplayName("LOW band covers 0 through 9")
    void lowBandBoundaries() {
        assertThat(scorer.levelOf(0)).isEqualTo(RiskLevel.LOW);
        assertThat(scorer.levelOf(9)).isEqualTo(RiskLevel.LOW);
        assertThat(scorer.levelOf(10)).isNotEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("MODERATE band covers 10 through 39")
    void moderateBandBoundaries() {
        assertThat(scorer.levelOf(10)).isEqualTo(RiskLevel.MODERATE);
        assertThat(scorer.levelOf(39)).isEqualTo(RiskLevel.MODERATE);
        assertThat(scorer.levelOf(40)).isNotEqualTo(RiskLevel.MODERATE);
    }

    @Test
    @DisplayName("HIGH band covers 40 through 69")
    void highBandBoundaries() {
        assertThat(scorer.levelOf(40)).isEqualTo(RiskLevel.HIGH);
        assertThat(scorer.levelOf(69)).isEqualTo(RiskLevel.HIGH);
        assertThat(scorer.levelOf(70)).isNotEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("CRITICAL band covers 70 through 100")
    void criticalBandBoundaries() {
        assertThat(scorer.levelOf(70)).isEqualTo(RiskLevel.CRITICAL);
        assertThat(scorer.levelOf(100)).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    @DisplayName("levelOf rejects a score outside 0..100")
    void levelOfRejectsOutOfRangeScore() {
        assertThatIllegalArgumentException().isThrownBy(() -> scorer.levelOf(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> scorer.levelOf(101));
    }

    private static RuleFinding finding(String ruleId, int points) {
        return RuleFinding.of(ruleId, Severity.MEDIUM, points, "title", "explanation");
    }
}
