package com.lyanhkhoa.linksentry.analysis.rules;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Flags links whose subdomain labels contain words associated with credential
 * phishing (e.g. {@code login}, {@code verify}, {@code secure-account}).
 *
 * <p>Deliberately restricted to subdomain labels, never the path: a word like
 * {@code login} in a subdomain is attacker-controlled and a real signal, but the
 * same word in a path is routine — {@code github.com/login} is a legitimate page.
 */
public final class SuspiciousKeywordsRule implements AnalysisRule {

    /** Stable machine-readable identifier for this rule. */
    public static final String RULE_ID = "SUSPICIOUS_KEYWORDS";

    private static final Severity SEVERITY = Severity.MEDIUM;

    private static final int POINTS = 20;

    private static final String TITLE = "Subdomain uses a sensitive-sounding word";

    private static final String EXPLANATION =
            "A subdomain in this address contains a word commonly used to impersonate "
                    + "login or account-verification pages. Legitimate services rarely need "
                    + "such words outside their own registered domain.";

    private final List<String> keywords;

    /**
     * @param keywords words to match, case-insensitively, against subdomain labels;
     *                 must be non-empty
     */
    public SuspiciousKeywordsRule(List<String> keywords) {
        Objects.requireNonNull(keywords, "keywords");
        if (keywords.isEmpty()) {
            throw new IllegalArgumentException("keywords must not be empty");
        }
        this.keywords = keywords.stream().map(k -> k.toLowerCase(Locale.ROOT)).toList();
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Optional<RuleFinding> analyze(NormalizedUrl url) {
        Objects.requireNonNull(url, "url");
        boolean matches = url.subdomains().stream()
                .map(label -> label.toLowerCase(Locale.ROOT))
                .anyMatch(label -> keywords.stream().anyMatch(label::contains));
        if (!matches) {
            return Optional.empty();
        }
        return Optional.of(RuleFinding.of(RULE_ID, SEVERITY, POINTS, TITLE, EXPLANATION));
    }
}
