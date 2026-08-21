package com.lyanhkhoa.linksentry.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisResult;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import com.lyanhkhoa.linksentry.analysis.normalization.IpAddressScope;
import com.lyanhkhoa.linksentry.analysis.normalization.IpAddressScopeClassifier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

class IpScopeRegressionCorpusTest {

    private final UrlAnalyzer analyzer = AnalyzerFixture.productionAnalyzer();
    private final IpAddressScopeClassifier classifier = new IpAddressScopeClassifier();

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(
            resources = "/ip-scope-regression-corpus.csv",
            numLinesToSkip = 1,
            delimiterString = "|",
            encoding = "UTF-8")
    void classifiesLiteralScopeWithoutDoubleScoring(
            String id,
            String url,
            IpAddressScope expectedScope,
            String expectedRuleId,
            int expectedScore,
            RiskLevel expectedRiskLevel) {
        AnalysisResult result = analyzer.analyze(url);

        assertThat(classifier.classify(result.normalizedUrl().host())).as(id).isEqualTo(expectedScope);
        assertThat(result.findings()).extracting(RuleFinding::ruleId).containsExactly(expectedRuleId);
        assertThat(result.score()).as(id).isEqualTo(expectedScore);
        assertThat(result.riskLevel()).as(id).isEqualTo(expectedRiskLevel);
    }
}
