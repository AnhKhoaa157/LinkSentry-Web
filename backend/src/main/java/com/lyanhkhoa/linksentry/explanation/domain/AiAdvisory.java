package com.lyanhkhoa.linksentry.explanation.domain;

import java.util.List;
import java.util.Objects;

/**
 * The entire output an {@link ExplanationProvider} is allowed to produce: one
 * concise, risk-oriented advisory sentence and one or two recommended actions.
 *
 * <p>A provider never sets or overrides risk level, key findings, severity, or
 * points — those are assembled deterministically by
 * {@code explanation.application.ExplanationService} from the already-retained
 * scan result. This type structurally cannot carry any of them.
 */
public record AiAdvisory(String summary, List<String> recommendedActions) {

    public AiAdvisory {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(recommendedActions, "recommendedActions");
        recommendedActions = List.copyOf(recommendedActions);
    }
}
