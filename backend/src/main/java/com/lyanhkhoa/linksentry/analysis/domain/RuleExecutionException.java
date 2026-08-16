package com.lyanhkhoa.linksentry.analysis.domain;

/**
 * Thrown when an {@link AnalysisRule} fails unexpectedly during
 * {@link UrlAnalyzer#analyze(String)}.
 *
 * <p>A rule is only ever handed a {@link NormalizedUrl}, but that URL's fields
 * (host, path, subdomains…) are still derived from attacker-controlled input, so
 * the failing rule's own exception message or stack trace could quote fragments of
 * it. To keep that out of logs, this exception's message carries only the failing
 * rule's stable {@link AnalysisRule#id()} and the failure's exception class name —
 * never the original message and never the original exception as a chained cause,
 * since either would resurface in a server log via
 * {@code GlobalExceptionHandler}'s generic exception logging.
 *
 * <p>Deliberately fails the whole scan rather than skipping the broken rule: a
 * score that silently omits one rule's contribution is a score the client cannot
 * tell apart from a score that rule genuinely had nothing to say about, which
 * defeats the product's explainability guarantee.
 */
public final class RuleExecutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RuleExecutionException(String ruleId, String failureType) {
        super("Rule execution failed [ruleId=" + ruleId + ", type=" + failureType + "]");
    }
}
