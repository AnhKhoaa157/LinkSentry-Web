package com.lyanhkhoa.linksentry.common.config;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.DefaultUrlAnalyzer;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import com.lyanhkhoa.linksentry.analysis.normalization.DefaultUrlNormalizer;
import com.lyanhkhoa.linksentry.analysis.normalization.UrlNormalizer;
import com.lyanhkhoa.linksentry.analysis.rules.EncodedCharactersRule;
import com.lyanhkhoa.linksentry.analysis.rules.ExcessiveSubdomainsRule;
import com.lyanhkhoa.linksentry.analysis.rules.ExcessiveUrlLengthRule;
import com.lyanhkhoa.linksentry.analysis.rules.IpLiteralHostRule;
import com.lyanhkhoa.linksentry.analysis.rules.KnownUrlShortenerRule;
import com.lyanhkhoa.linksentry.analysis.rules.MissingHttpsRule;
import com.lyanhkhoa.linksentry.analysis.rules.PunycodeHostRule;
import com.lyanhkhoa.linksentry.analysis.rules.SuspiciousKeywordsRule;
import com.lyanhkhoa.linksentry.analysis.scoring.DefaultRiskScorer;
import com.lyanhkhoa.linksentry.analysis.scoring.RiskScorer;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free analysis engine into the Spring context.
 *
 * <p>Every class in {@code analysis.*} is deliberately free of Spring annotations
 * so it can be constructed and tested in plain unit tests. This is the one place
 * that knows about both the domain and the framework: it reads tunable values from
 * {@link RulesProperties} and assembles the rule list, the scorer, and the
 * analyzer that orchestrates them.
 */
@Configuration
class AnalysisConfig {

    @Bean
    UrlNormalizer urlNormalizer() {
        return new DefaultUrlNormalizer();
    }

    @Bean
    RiskScorer riskScorer() {
        return new DefaultRiskScorer();
    }

    /**
     * The ordered rule set, in the order they are documented in
     * {@code analysis.rules.package-info}. Order here does not affect the response —
     * {@link DefaultUrlAnalyzer} sorts findings independently — but a stable order
     * keeps this list easy to diff as rules are added.
     */
    @Bean
    List<AnalysisRule> analysisRules(RulesProperties rulesProperties) {
        return List.of(
                new MissingHttpsRule(),
                new IpLiteralHostRule(),
                new ExcessiveUrlLengthRule(rulesProperties.excessiveUrlLength().maxLength()),
                new ExcessiveSubdomainsRule(rulesProperties.excessiveSubdomains().maxDepth()),
                new SuspiciousKeywordsRule(rulesProperties.suspiciousKeywords().keywords()),
                new PunycodeHostRule(),
                new EncodedCharactersRule(),
                new KnownUrlShortenerRule(rulesProperties.knownUrlShorteners().domains()));
    }

    @Bean
    UrlAnalyzer urlAnalyzer(UrlNormalizer urlNormalizer, List<AnalysisRule> analysisRules, RiskScorer riskScorer) {
        return new DefaultUrlAnalyzer(urlNormalizer, analysisRules, riskScorer);
    }
}
