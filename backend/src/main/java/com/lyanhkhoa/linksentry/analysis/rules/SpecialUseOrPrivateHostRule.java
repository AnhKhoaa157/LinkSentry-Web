package com.lyanhkhoa.linksentry.analysis.rules;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import com.lyanhkhoa.linksentry.analysis.normalization.IpAddressScope;
import com.lyanhkhoa.linksentry.analysis.normalization.IpAddressScopeClassifier;
import java.util.Objects;
import java.util.Optional;

/** Flags IP literals that are private, local, documentation, or otherwise special-use. */
public final class SpecialUseOrPrivateHostRule implements AnalysisRule {

    public static final String RULE_ID = "SPECIAL_USE_OR_PRIVATE_HOST";

    private static final Severity SEVERITY = Severity.MEDIUM;
    private static final int POINTS = 15;
    private static final String TITLE = "Address uses a private or special-use IP range";
    private static final String EXPLANATION =
            "This link points directly at an address reserved for private, local, "
                    + "documentation, or another special purpose. It is not an ordinary "
                    + "public website address and deserves extra verification.";

    private static final IpAddressScopeClassifier SCOPE_CLASSIFIER = new IpAddressScopeClassifier();

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Optional<RuleFinding> analyze(NormalizedUrl url) {
        Objects.requireNonNull(url, "url");

        IpAddressScope scope = SCOPE_CLASSIFIER.classify(url.host());
        if (!url.ipLiteral() || !scope.isSpecialUseOrPrivateIpLiteral()) {
            return Optional.empty();
        }
        return Optional.of(RuleFinding.of(RULE_ID, SEVERITY, POINTS, TITLE, EXPLANATION, scope.evidence()));
    }
}
