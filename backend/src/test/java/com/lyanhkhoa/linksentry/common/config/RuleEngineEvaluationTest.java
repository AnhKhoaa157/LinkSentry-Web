package com.lyanhkhoa.linksentry.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.common.config.RuleEngineEvaluation.Confusion;
import com.lyanhkhoa.linksentry.common.config.RuleEngineEvaluation.LabeledUrl;
import com.lyanhkhoa.linksentry.common.config.RuleEngineEvaluation.ProductionEngine;
import com.lyanhkhoa.linksentry.common.config.RuleEngineEvaluation.Report;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleEngineEvaluationTest {

    private static final String CAUGHT = "https://login.vietcombank.verify-account.xyz/";
    private static final String MISSED = "https://plain-looking.com/offer";
    private static final String CLEAN = "https://example.com/";
    private static final String INVALID = "ftp://files.example/";

    private final ProductionEngine engine = RuleEngineEvaluation.productionEngine();

    @Test
    @DisplayName("the engine is built from application.yml with every production rule, in configured order")
    void buildsTheProductionRuleSet() {
        assertThat(engine.ruleIds())
                .hasSize(12)
                .startsWith("MISSING_HTTPS")
                .contains("BRAND_DOMAIN_MISMATCH", "BRAND_IMPERSONATION_APK")
                .endsWith("KNOWN_URL_SHORTENER");
        assertThat(engine.analyzer().analyze(CAUGHT).riskLevel()).isGreaterThanOrEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("reads the ml/ dataset schema, including quoted commas, doubled quotes, a BOM, and CRLF")
    void parsesDatasetSchema() {
        String csv = "﻿url,label,source,notes\r\n"
                + "\"https://example.com/?a=1,b=2\",1,fixture,\"has, comma\"\r\n"
                + "\r\n"
                + "https://example.org/,0,fixture,\"say \"\"hi\"\"\"\n";

        assertThat(RuleEngineEvaluation.parseDataset(csv)).containsExactly(
                new LabeledUrl("https://example.com/?a=1,b=2", true),
                new LabeledUrl("https://example.org/", false));
    }

    @Test
    @DisplayName("dataset errors name the row, never the URL")
    void datasetErrorsNeverEchoUrls() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RuleEngineEvaluation.parseDataset("url,label\n" + CAUGHT + ",maybe\n"))
                .withMessage("Row 2 has a label other than 0 or 1")
                .withMessageNotContaining("vietcombank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RuleEngineEvaluation.parseDataset("link,verdict\n" + CAUGHT + ",1\n"))
                .withMessageNotContaining("vietcombank");
    }

    @Test
    @DisplayName("counts the confusion matrix per threshold and keeps rejected input out of it")
    void computesConfusionMatrix() {
        Report report = RuleEngineEvaluation.evaluate(engine, List.of(
                new LabeledUrl(CAUGHT, true),
                new LabeledUrl(MISSED, true),
                new LabeledUrl(CLEAN, false),
                new LabeledUrl(INVALID, false)));

        Confusion moderate = report.confusionAt(RiskLevel.MODERATE);
        assertThat(moderate.truePositives()).isEqualTo(1);
        assertThat(moderate.falseNegatives()).isEqualTo(1);
        assertThat(moderate.trueNegatives()).isEqualTo(1);
        assertThat(moderate.falsePositives()).isZero();
        assertThat(moderate.precision()).isEqualTo(1.0);
        assertThat(moderate.recall()).isEqualTo(0.5);
        assertThat(report.rejectedBenign()).isEqualTo(1);
        assertThat(report.rejectedRisky()).isZero();
        assertThat(report.ruleFirings().get("BRAND_DOMAIN_MISMATCH")).containsExactly(1, 0);
    }

    @Test
    @DisplayName("the report holds only counts and rates, never a submitted URL")
    void reportNeverPrintsUrls() {
        String markdown = RuleEngineEvaluation.evaluate(engine, List.of(
                        new LabeledUrl(CAUGHT, true), new LabeledUrl(CLEAN, false), new LabeledUrl(INVALID, false)))
                .toMarkdown("fixture.csv");

        assertThat(markdown)
                .contains("| MODERATE |", "| BRAND_DOMAIN_MISMATCH |", "Rejected as invalid input")
                .doesNotContain("vietcombank", "verify-account", "example.com", "files.example");
    }

    @Test
    @DisplayName("an empty side of the matrix reads as n/a instead of dividing by zero")
    void emptyDatasetReportsNotApplicable() {
        String markdown = RuleEngineEvaluation.evaluate(engine, List.of()).toMarkdown("empty.csv");

        assertThat(markdown).contains("| MODERATE | 0 | 0 | 0 | 0 | n/a | n/a | n/a | n/a |");
    }
}
