package com.lyanhkhoa.linksentry.explanation.domain;

/**
 * Safe, provider-agnostic failure signal from an {@link ExplanationProvider}.
 *
 * <p>Deliberately carries only a fixed, safe message and never the original
 * exception as a cause — the same discipline as
 * {@code analysis.domain.RuleExecutionException} and for the same reason: a
 * wrapped provider exception could otherwise carry a fragment of the provider's
 * request or response body into a log via this exception's stack trace.
 */
public final class ExplanationProviderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExplanationProviderException(String safeMessage) {
        super(safeMessage);
    }
}
