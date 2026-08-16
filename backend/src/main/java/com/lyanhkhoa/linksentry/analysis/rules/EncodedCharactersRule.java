package com.lyanhkhoa.linksentry.analysis.rules;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Flags links whose path contains percent-encoded characters.
 *
 * <p><strong>Scope, stated precisely so this rule is never assumed to catch more
 * than it does:</strong> it inspects only {@link NormalizedUrl#path()}, and only
 * for the presence of a {@code %XX} sequence — never the decoded content, never
 * the query or fragment (which routinely carry legitimate tokens and are not
 * evidence of obfuscation on their own), and it does not attempt to detect nested
 * or double percent-encoding. It also does not re-check the authority component
 * for {@code @}-based user-info tricks — {@link
 * com.lyanhkhoa.linksentry.analysis.normalization.UrlNormalizer} already rejects
 * those as an invalid URL before any rule runs, so a rule never sees one.
 *
 * <p>Encoding in a path can be entirely ordinary, but it is also a way to obscure
 * a suspicious path segment from a quick visual read, so its mere presence is
 * surfaced as a weak signal.
 */
public final class EncodedCharactersRule implements AnalysisRule {

    /** Stable machine-readable identifier for this rule. */
    public static final String RULE_ID = "ENCODED_CHARACTERS";

    private static final Severity SEVERITY = Severity.LOW;

    private static final int POINTS = 10;

    private static final String TITLE = "Link path contains encoded characters";

    private static final String EXPLANATION =
            "Part of this address's path is percent-encoded rather than shown in plain "
                    + "text. This is often harmless, but it can also be used to disguise "
                    + "characters that would otherwise look suspicious at a glance.";

    private static final Pattern PERCENT_ENCODED = Pattern.compile("%[0-9A-Fa-f]{2}");

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Optional<RuleFinding> analyze(NormalizedUrl url) {
        Objects.requireNonNull(url, "url");
        if (!PERCENT_ENCODED.matcher(url.path()).find()) {
            return Optional.empty();
        }
        return Optional.of(RuleFinding.of(RULE_ID, SEVERITY, POINTS, TITLE, EXPLANATION));
    }
}
