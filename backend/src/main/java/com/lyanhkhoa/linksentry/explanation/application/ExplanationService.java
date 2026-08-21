package com.lyanhkhoa.linksentry.explanation.application;

import com.lyanhkhoa.linksentry.common.config.AiExplanationProperties;
import com.lyanhkhoa.linksentry.explanation.domain.AiAdvisory;
import com.lyanhkhoa.linksentry.explanation.domain.ExplanationProvider;
import com.lyanhkhoa.linksentry.explanation.domain.ExplanationProviderException;
import com.lyanhkhoa.linksentry.explanation.domain.ExplanationResult;
import com.lyanhkhoa.linksentry.explanation.domain.KeyFinding;
import com.lyanhkhoa.linksentry.explanation.domain.ScanSummary;
import com.lyanhkhoa.linksentry.history.application.ScanHistoryService;
import com.lyanhkhoa.linksentry.history.application.ScanIdParser;
import com.lyanhkhoa.linksentry.history.application.ScanNotFoundException;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.StoredFinding;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Application boundary for the optional, advisory AI scan explanation.
 *
 * <p>Reuses {@link ScanHistoryService#findRetained(UUID, UUID)} — the same
 * license-scoped, retention-aware lookup {@code ScanService} uses — so a missing,
 * malformed, expired, ownerless, or another license's scan ID produces the
 * identical safe {@link ScanNotFoundException} here as everywhere else. Nothing
 * about explanation availability changes that lookup or its result.
 */
@Service
public class ExplanationService {

    private static final Logger log = LoggerFactory.getLogger(ExplanationService.class);

    // "Limit deterministic key findings to the most useful 3, preserving existing
    // finding order" — the scan's existing order is already the deterministic,
    // most-significant-first order the rule engine produces.
    private static final int MAX_KEY_FINDINGS = 3;

    private final AiExplanationProperties properties;
    private final ExplanationProvider provider;
    private final ScanHistoryService historyService;

    public ExplanationService(
            AiExplanationProperties properties, ExplanationProvider provider, ScanHistoryService historyService) {
        this.properties = properties;
        this.provider = provider;
        this.historyService = historyService;
    }

    /**
     * Produces a structured, advisory explanation of one retained scan owned by the caller's license.
     *
     * <p>{@code riskLevel} and {@code keyFindings} come straight from the retained
     * {@link ScanHistory}, deterministically and without AI involvement; only
     * {@code summary} and {@code recommendedActions} come from the provider. A
     * provider failure — disabled feature, timeout, malformed or invalid
     * structured output — fails the whole call; there is no partial, AI-less
     * response.
     *
     * @param rawScanId      opaque scan ID path value, as submitted
     * @param ownerLicenseId authenticated caller's license; the endpoint requires a
     *                       licensed device, so this is never null in production, but
     *                       a defensive null still yields the same safe
     *                       {@link ScanNotFoundException} as an unowned scan
     * @throws ScanNotFoundException          for a missing, malformed, expired,
     *                                          ownerless, or cross-owner scan
     * @throws ExplanationUnavailableException when the feature is disabled or the
     *                                          provider could not produce a result
     */
    public ExplanationResult explain(String rawScanId, UUID ownerLicenseId) {
        if (!properties.enabled()) {
            throw new ExplanationUnavailableException();
        }
        if (ownerLicenseId == null) {
            throw new ScanNotFoundException();
        }
        UUID scanId = ScanIdParser.parse(rawScanId);
        ScanHistory history =
                historyService.findRetained(scanId, ownerLicenseId).orElseThrow(ScanNotFoundException::new);

        ScanSummary summary = toSummary(history);
        try {
            AiAdvisory advisory = provider.explain(summary);
            return new ExplanationResult(
                    history.riskLevel(), toKeyFindings(history), advisory.summary(), advisory.recommendedActions());
        } catch (ExplanationProviderException exception) {
            // Never the provider's own message: it may quote request or response detail.
            log.warn("AI explanation provider could not produce a result");
            throw new ExplanationUnavailableException();
        }
    }

    static ScanSummary toSummary(ScanHistory history) {
        List<ScanSummary.FindingSummary> findings = history.findings().stream()
                .map(ExplanationService::toFindingSummary)
                .toList();
        return new ScanSummary(history.score(), history.riskLevel(), history.engineVersion(), findings);
    }

    private static ScanSummary.FindingSummary toFindingSummary(StoredFinding finding) {
        return new ScanSummary.FindingSummary(
                finding.ruleId(), finding.severity(), finding.points(), finding.title(), finding.explanation());
    }

    /**
     * Deterministically assembled from the retained scan's own finding order —
     * never from the AI provider. Caps at {@link #MAX_KEY_FINDINGS}, preserving
     * the scan's existing order, and drops {@code ruleId} and {@code evidence}.
     */
    static List<KeyFinding> toKeyFindings(ScanHistory history) {
        return history.findings().stream()
                .limit(MAX_KEY_FINDINGS)
                .map(finding -> new KeyFinding(finding.title(), finding.explanation(), finding.severity(), finding.points()))
                .toList();
    }
}
