package com.lyanhkhoa.linksentry.analysis;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.DefaultUrlAnalyzer;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import com.lyanhkhoa.linksentry.analysis.normalization.DefaultUrlNormalizer;
import com.lyanhkhoa.linksentry.analysis.rules.Brand;
import com.lyanhkhoa.linksentry.analysis.rules.BrandDomainMismatchRule;
import com.lyanhkhoa.linksentry.analysis.rules.BrandLookalikeRule;
import com.lyanhkhoa.linksentry.analysis.rules.BrandRegistry;
import com.lyanhkhoa.linksentry.analysis.rules.EncodedCharactersRule;
import com.lyanhkhoa.linksentry.analysis.rules.ExcessiveSubdomainsRule;
import com.lyanhkhoa.linksentry.analysis.rules.ExcessiveUrlLengthRule;
import com.lyanhkhoa.linksentry.analysis.rules.IpLiteralHostRule;
import com.lyanhkhoa.linksentry.analysis.rules.KnownUrlShortenerRule;
import com.lyanhkhoa.linksentry.analysis.rules.MissingHttpsRule;
import com.lyanhkhoa.linksentry.analysis.rules.PunycodeHostRule;
import com.lyanhkhoa.linksentry.analysis.rules.SpecialUseOrPrivateHostRule;
import com.lyanhkhoa.linksentry.analysis.rules.SuspiciousKeywordsRule;
import com.lyanhkhoa.linksentry.analysis.scoring.DefaultRiskScorer;
import java.util.List;

/**
 * Builds a {@link UrlAnalyzer} wired exactly like production ({@code
 * AnalysisConfig}, with the same defaults as {@code application.yml}), but
 * framework-free — plain constructors, no Spring context.
 *
 * <p>Shared by every end-to-end corpus test ({@link RegressionCorpusTest},
 * {@link BrandRegressionCorpusTest}) so the mirrored rule list, thresholds, and
 * brand registry are declared exactly once. If {@code application.yml} or {@code
 * AnalysisConfig} changes, this is the one place a test author needs to update to
 * keep every corpus in sync.
 */
final class AnalyzerFixture {

    // Mirrors application.yml's linksentry.rules.* defaults.
    private static final List<String> SUSPICIOUS_KEYWORDS =
            List.of("login", "verify", "secure", "account", "update", "confirm", "signin", "banking", "password",
                    "billing");
    private static final List<String> KNOWN_SHORTENERS =
            List.of("bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly", "adf.ly", "rebrand.ly",
                    "cutt.ly", "shorturl.at", "rb.gy");

    // Mirrors application.yml's linksentry.brands.entries defaults, in configured
    // order — order is significant for the multi-brand tie-break both brand rules
    // apply.
    private static final Brand VIETCOMBANK =
            new Brand("vietcombank", "Vietcombank", List.of("vietcombank"), List.of("vietcombank.com.vn"));
    private static final Brand TECHCOMBANK =
            new Brand("techcombank", "Techcombank", List.of("techcombank"), List.of("techcombank.com.vn"));
    private static final Brand BIDV = new Brand("bidv", "BIDV", List.of("bidv"), List.of("bidv.com.vn"));
    private static final Brand VIETINBANK =
            new Brand("vietinbank", "VietinBank", List.of("vietinbank"), List.of("vietinbank.vn"));
    private static final Brand AGRIBANK =
            new Brand("agribank", "Agribank", List.of("agribank"), List.of("agribank.com.vn"));
    private static final Brand ACB = new Brand("acb", "ACB", List.of("acb"), List.of("acb.com.vn"));
    private static final Brand SACOMBANK =
            new Brand("sacombank", "Sacombank", List.of("sacombank"), List.of("sacombank.com.vn"));
    private static final Brand MOMO = new Brand("momo", "MoMo", List.of("momo"), List.of("momo.vn"));
    private static final Brand SHOPEE = new Brand("shopee", "Shopee", List.of("shopee"), List.of("shopee.vn"));
    private static final Brand TIKI = new Brand("tiki", "Tiki", List.of("tiki"), List.of("tiki.vn"));

    private AnalyzerFixture() {
    }

    static UrlAnalyzer productionAnalyzer() {
        return new DefaultUrlAnalyzer(new DefaultUrlNormalizer(), productionRules(), new DefaultRiskScorer());
    }

    /** The ordered rule list exactly as {@code AnalysisConfig.analysisRules} wires it. */
    static List<AnalysisRule> productionRules() {
        BrandRegistry brandRegistry = productionBrandRegistry();
        return List.of(
                new MissingHttpsRule(),
                new IpLiteralHostRule(),
                new SpecialUseOrPrivateHostRule(),
                new ExcessiveUrlLengthRule(100),
                new ExcessiveSubdomainsRule(3),
                new SuspiciousKeywordsRule(SUSPICIOUS_KEYWORDS),
                new BrandDomainMismatchRule(brandRegistry),
                new BrandLookalikeRule(brandRegistry),
                new PunycodeHostRule(),
                new EncodedCharactersRule(),
                new KnownUrlShortenerRule(KNOWN_SHORTENERS));
    }

    /** All ten curated brands, in {@code application.yml}'s configured order. */
    static BrandRegistry productionBrandRegistry() {
        return new BrandRegistry(List.of(
                VIETCOMBANK, TECHCOMBANK, BIDV, VIETINBANK, AGRIBANK, ACB, SACOMBANK, MOMO, SHOPEE, TIKI));
    }
}
