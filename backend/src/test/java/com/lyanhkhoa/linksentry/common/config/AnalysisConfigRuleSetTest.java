package com.lyanhkhoa.linksentry.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.rules.Brand;
import com.lyanhkhoa.linksentry.analysis.rules.BrandRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisConfigRuleSetTest {

    @Test
    void productionWiringContainsTheDocumentedElevenRulesInStableOrder() {
        RulesProperties properties = new RulesProperties(
                new RulesProperties.ExcessiveUrlLength(100),
                new RulesProperties.ExcessiveSubdomains(3),
                new RulesProperties.SuspiciousKeywords(List.of("login")),
                new RulesProperties.KnownUrlShorteners(List.of("bit.ly")));
        BrandRegistry brandRegistry = new BrandRegistry(List.of(
                new Brand("example", "Example", List.of("example"), List.of("example.com"))));

        List<AnalysisRule> rules = new AnalysisConfig().analysisRules(properties, brandRegistry);

        assertThat(rules)
                .extracting(AnalysisRule::id)
                .containsExactly(
                        "MISSING_HTTPS",
                        "IP_LITERAL_HOST",
                        "SPECIAL_USE_OR_PRIVATE_HOST",
                        "EXCESSIVE_URL_LENGTH",
                        "EXCESSIVE_SUBDOMAINS",
                        "SUSPICIOUS_KEYWORDS",
                        "BRAND_DOMAIN_MISMATCH",
                        "BRAND_LOOKALIKE_HOSTNAME",
                        "PUNYCODE_HOST",
                        "ENCODED_CHARACTERS",
                        "KNOWN_URL_SHORTENER");
    }
}
