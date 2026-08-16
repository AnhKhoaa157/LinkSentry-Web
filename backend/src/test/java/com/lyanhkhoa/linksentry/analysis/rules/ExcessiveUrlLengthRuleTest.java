package com.lyanhkhoa.linksentry.analysis.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExcessiveUrlLengthRuleTest {

    // "https://example.com" is 19 characters; the threshold below leaves room to
    // craft inputs that land exactly on, under, and over the boundary.
    private static final String BASE = "https://example.com";
    private final ExcessiveUrlLengthRule rule = new ExcessiveUrlLengthRule(25);

    @Test
    @DisplayName("a raw input longer than the threshold produces a finding")
    void tooLongProducesFinding() {
        NormalizedUrl url = urlWithRawAndRedacted(
                BASE + "/this-path-is-definitely-too-long-for-the-threshold",
                BASE + "/this-path-is-definitely-too-long-for-the-threshold");

        assertThat(rule.analyze(url)).isPresent();
        assertThat(rule.analyze(url).get().ruleId()).isEqualTo(ExcessiveUrlLengthRule.RULE_ID);
    }

    @Test
    @DisplayName("a raw input at or under the threshold produces no finding")
    void withinThresholdProducesNoFinding() {
        NormalizedUrl url = urlWithRawAndRedacted(BASE + "/ok", BASE + "/ok");

        assertThat(url.originalInput()).hasSizeLessThan(25);
        assertThat(rule.analyze(url)).isEmpty();
    }

    @Test
    @DisplayName("exactly at the threshold is not flagged")
    void exactlyAtThresholdIsNotFlagged() {
        NormalizedUrl exact = urlWithRawAndRedacted(BASE + "/abcde", BASE + "/abcde");

        assertThat(exact.originalInput()).hasSize(25);
        assertThat(rule.analyze(exact)).isEmpty();
    }

    @Test
    @DisplayName("one character over the threshold is flagged")
    void oneOverThresholdIsFlagged() {
        NormalizedUrl overByOne = urlWithRawAndRedacted(BASE + "/abcdef", BASE + "/abcdef");

        assertThat(overByOne.originalInput()).hasSize(26);
        assertThat(rule.analyze(overByOne)).isPresent();
    }

    @Test
    @DisplayName("a long query string alone crosses the threshold, even though the redacted value stays short")
    void longQueryAloneCrossesThreshold() {
        String redacted = BASE + "/x";
        String raw = redacted + "?token=" + "a".repeat(40);
        NormalizedUrl url = urlWithRawAndRedacted(raw, redacted);

        assertThat(redacted).hasSizeLessThan(25);
        assertThat(rule.analyze(url)).isPresent();
    }

    @Test
    @DisplayName("a long fragment alone crosses the threshold, even though the redacted value stays short")
    void longFragmentAloneCrossesThreshold() {
        String redacted = BASE + "/x";
        String raw = redacted + "#" + "a".repeat(40);
        NormalizedUrl url = urlWithRawAndRedacted(raw, redacted);

        assertThat(redacted).hasSizeLessThan(25);
        assertThat(rule.analyze(url)).isPresent();
    }

    @Test
    @DisplayName("the finding never carries evidence or otherwise quotes the raw input")
    void findingNeverQuotesRawInput() {
        String redacted = BASE + "/x";
        String raw = redacted + "?token=super-secret-value";
        NormalizedUrl url = urlWithRawAndRedacted(raw, redacted);

        var finding = rule.analyze(url).orElseThrow();
        assertThat(finding.evidence()).isNull();
        assertThat(finding.explanation()).doesNotContain("token");
        assertThat(finding.explanation()).doesNotContain("super-secret-value");
        assertThat(finding.title()).doesNotContain("super-secret-value");
    }

    @Test
    @DisplayName("rejects a non-positive threshold")
    void rejectsNonPositiveThreshold() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ExcessiveUrlLengthRule(0));
    }

    private static NormalizedUrl urlWithRawAndRedacted(String raw, String redacted) {
        return new NormalizedUrl(
                raw, redacted, "https", "example.com", "example.com", "example.com", List.of(), null, "/", false,
                false, false);
    }
}
