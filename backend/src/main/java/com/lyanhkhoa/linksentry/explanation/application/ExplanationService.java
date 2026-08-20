package com.lyanhkhoa.linksentry.explanation.application;

import com.lyanhkhoa.linksentry.common.config.AiExplanationProperties;
import com.lyanhkhoa.linksentry.explanation.domain.ExplanationProvider;
import com.lyanhkhoa.linksentry.explanation.domain.ExplanationProviderException;
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
     * Produces a short, advisory explanation of one retained scan owned by the caller's license.
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
    public String explain(String rawScanId, UUID ownerLicenseId) {
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
            return provider.explain(summary);
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
}
