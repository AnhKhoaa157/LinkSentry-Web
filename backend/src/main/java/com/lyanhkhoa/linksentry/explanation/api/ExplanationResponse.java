package com.lyanhkhoa.linksentry.explanation.api;

/**
 * Envelope of {@code POST /api/v1/scans/{scanId}/explanation}.
 *
 * @param data the generated explanation
 */
public record ExplanationResponse(ExplanationData data) {

    /** @param explanation short, plain-text, risk-oriented AI explanation */
    public record ExplanationData(String explanation) {}
}
