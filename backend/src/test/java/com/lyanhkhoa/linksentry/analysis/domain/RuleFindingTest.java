package com.lyanhkhoa.linksentry.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the invariants {@link RuleFinding} actually enforces today.
 *
 * <p>Nothing here tests analysis behaviour — no rule exists yet. These assertions
 * exist so that a future rule cannot quietly produce a finding that breaks the
 * explainability contract.
 */
class RuleFindingTest {

    @Test
    @DisplayName("accepts a well-formed finding")
    void acceptsWellFormedFinding() {
        RuleFinding finding = RuleFinding.of(
                "EXCESSIVE_SUBDOMAINS",
                Severity.MEDIUM,
                20,
                "Unusually deep subdomain structure",
                "The hostname contains more subdomain levels than expected.");

        assertThat(finding.ruleId()).isEqualTo("EXCESSIVE_SUBDOMAINS");
        assertThat(finding.points()).isEqualTo(20);
        assertThat(finding.evidence()).isNull();
    }

    @Test
    @DisplayName("rejects a blank rule id")
    void rejectsBlankRuleId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> RuleFinding.of("  ", Severity.LOW, 1, "Title", "Explanation."))
                .withMessageContaining("ruleId");
    }

    @Test
    @DisplayName("rejects negative points, which would make a score unexplainable")
    void rejectsNegativePoints() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> RuleFinding.of("SOME_RULE", Severity.LOW, -1, "Title", "Explanation."))
                .withMessageContaining("points");
    }

    @Test
    @DisplayName("allows zero points for a purely informational finding")
    void allowsZeroPoints() {
        assertThat(RuleFinding.of("PUNYCODE_HOST", Severity.INFO, 0, "Title", "Explanation.")
                        .points())
                .isZero();
    }

    @Test
    @DisplayName("requires the explainability fields")
    void requiresExplainabilityFields() {
        assertThatNullPointerException()
                .isThrownBy(() -> RuleFinding.of("SOME_RULE", Severity.LOW, 1, null, "Explanation."));
        assertThatNullPointerException().isThrownBy(() -> RuleFinding.of("SOME_RULE", Severity.LOW, 1, "Title", null));
        assertThatNullPointerException().isThrownBy(() -> RuleFinding.of("SOME_RULE", null, 1, "Title", "Because."));
    }
}
