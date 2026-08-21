package com.lyanhkhoa.linksentry.explanation.api;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import com.lyanhkhoa.linksentry.explanation.domain.ExplanationResult;
import java.util.List;

/**
 * Envelope of {@code POST /api/v1/scans/{scanId}/explanation}.
 *
 * <p>{@code riskLevel} and {@code keyFindings} are assembled deterministically
 * by the backend from the retained scan result; only {@code summary} and
 * {@code recommendedActions} are AI-produced. See
 * {@code docs/adr/0005-deepseek-scan-explanation-integration.md}.
 *
 * <p>{@code explanation} is a <strong>deprecated legacy field</strong>, kept
 * additively for existing v1 consumers built against the pre-M8 shape
 * ({@code {"data":{"explanation":"..."}}}). It is never a second, independent
 * value: it is always set to exactly {@code summary}, so there is only one AI
 * sentence in the response, surfaced under two keys during the deprecation
 * window. New callers should read {@code summary} instead; the frontend
 * already does and never renders {@code explanation}. This is a
 * backward-compatible addition, not a breaking mutation of {@code /api/v1} —
 * see {@code docs/API_CONTRACT.md} contract rule 10.
 *
 * @param data the generated explanation
 */
public record ExplanationResponse(ExplanationData data) {

    /**
     * @param riskLevel          backend-owned risk band of the retained scan
     * @param keyFindings        up to 3 backend-owned findings, in the scan's existing order
     * @param summary            one concise, risk-oriented AI advisory sentence
     * @param recommendedActions 1-2 AI-produced recommended actions
     * @param explanation        deprecated legacy alias, always equal to {@code summary}
     */
    public record ExplanationData(
            RiskLevel riskLevel,
            List<KeyFindingDto> keyFindings,
            String summary,
            List<String> recommendedActions,
            String explanation) {}

    /** @param explanation the rule's own generic, user-safe explanation — never evidence */
    public record KeyFindingDto(String title, String explanation, Severity severity, int points) {}

    public static ExplanationResponse from(ExplanationResult result) {
        List<KeyFindingDto> keyFindings = result.keyFindings().stream()
                .map(finding -> new KeyFindingDto(
                        finding.title(), finding.explanation(), finding.severity(), finding.points()))
                .toList();
        return new ExplanationResponse(new ExplanationData(
                result.riskLevel(),
                keyFindings,
                result.summary(),
                result.recommendedActions(),
                result.summary()));
    }
}
