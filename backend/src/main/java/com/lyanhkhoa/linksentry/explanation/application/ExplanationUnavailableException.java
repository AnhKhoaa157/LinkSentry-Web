package com.lyanhkhoa.linksentry.explanation.application;

/**
 * Safe application error for every way an AI explanation can fail to be produced:
 * the feature is disabled, configuration is missing, the provider timed out,
 * the provider failed, or the provider's response was unusable. One fixed,
 * vendor-free message covers every cause — the client is never told which.
 */
public final class ExplanationUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExplanationUnavailableException() {
        super("AI explanation is not available.");
    }
}
