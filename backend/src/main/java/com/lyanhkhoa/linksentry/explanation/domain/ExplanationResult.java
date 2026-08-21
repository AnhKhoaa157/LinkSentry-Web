package com.lyanhkhoa.linksentry.explanation.domain;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import java.util.List;
import java.util.Objects;

/**
 * The full result of {@code explanation.application.ExplanationService#explain}:
 * a deterministic, backend-owned {@code riskLevel} and {@code keyFindings}, plus
 * an AI-produced advisory {@code summary} and {@code recommendedActions}.
 *
 * <p>{@code riskLevel} and {@code keyFindings} are read straight from the
 * retained scan result and are never influenced by the AI provider; only
 * {@code summary} and {@code recommendedActions} originate from
 * {@link AiAdvisory}. See {@code docs/adr/0005-deepseek-scan-explanation-integration.md}.
 */
public record ExplanationResult(
        RiskLevel riskLevel, List<KeyFinding> keyFindings, String summary, List<String> recommendedActions) {

    public ExplanationResult {
        Objects.requireNonNull(riskLevel, "riskLevel");
        Objects.requireNonNull(keyFindings, "keyFindings");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(recommendedActions, "recommendedActions");
        keyFindings = List.copyOf(keyFindings);
        recommendedActions = List.copyOf(recommendedActions);
    }
}
