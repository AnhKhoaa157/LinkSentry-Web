package com.lyanhkhoa.linksentry.scan.application;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisResult;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import com.lyanhkhoa.linksentry.common.api.ResponseLocale;
import com.lyanhkhoa.linksentry.common.config.EngineProperties;
import com.lyanhkhoa.linksentry.history.application.ScanHistoryService;
import com.lyanhkhoa.linksentry.history.application.ScanHistoryPage;
import com.lyanhkhoa.linksentry.history.application.ScanIdParser;
import com.lyanhkhoa.linksentry.history.application.ScanNotFoundException;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryFindingCount;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryInsightsAggregate;
import com.lyanhkhoa.linksentry.history.domain.StoredFinding;
import com.lyanhkhoa.linksentry.history.domain.StoredNormalizedUrl;
import com.lyanhkhoa.linksentry.scan.api.FindingResponse;
import com.lyanhkhoa.linksentry.scan.api.NormalizedUrlResponse;
import com.lyanhkhoa.linksentry.scan.api.ScanDataResponse;
import com.lyanhkhoa.linksentry.scan.api.ScanHistoryListResponse;
import com.lyanhkhoa.linksentry.scan.api.ScanInsightsResponse;
import com.lyanhkhoa.linksentry.scan.api.ScanResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sits between {@link com.lyanhkhoa.linksentry.scan.api.ScanController} and
 * {@link UrlAnalyzer}.
 *
 * <p>Owns what {@link UrlAnalyzer#analyze(String)} deliberately leaves out — the
 * scan id, the UTC timestamp, and the engine version — so the domain stays a pure
 * function of its input. Also the only place in the scan feature permitted to log:
 * the scan id and the rule ids that fired, never the submitted URL.
 */
@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final UrlAnalyzer urlAnalyzer;
    private final EngineProperties engineProperties;
    private final Clock clock;
    private final ScanHistoryService historyService;

    public ScanService(
            UrlAnalyzer urlAnalyzer,
            EngineProperties engineProperties,
            Clock clock,
            ScanHistoryService historyService) {
        this.urlAnalyzer = urlAnalyzer;
        this.engineProperties = engineProperties;
        this.clock = clock;
        this.historyService = historyService;
    }

    /**
     * Analyses {@code rawInput} and stamps the result with a fresh scan identity.
     *
     * @param rawInput the raw submitted URL
     * @return the complete envelope ready to serialise
     * @throws com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException when
     *                             {@code rawInput} is not an analysable URL
     */
    public ScanResponse scan(String rawInput) {
        return scan(rawInput, null, ResponseLocale.ENGLISH);
    }

    /**
     * Analyses one URL. A trial (unlicensed) request receives the safe result in memory and
     * is deliberately neither assigned a retrievable ID nor persisted.
     */
    public ScanResponse scan(String rawInput, UUID ownerLicenseId) {
        return scan(rawInput, ownerLicenseId, ResponseLocale.ENGLISH);
    }

    /**
     * Analyses one URL and maps only its server-owned presentation copy to the
     * requested response locale. The analysis result and persisted snapshot stay
     * locale-independent.
     */
    public ScanResponse scan(String rawInput, UUID ownerLicenseId, ResponseLocale locale) {
        Objects.requireNonNull(locale, "locale");
        AnalysisResult result = urlAnalyzer.analyze(rawInput);

        Instant analyzedAt = Instant.now(clock);
        if (ownerLicenseId == null) {
            List<String> ruleIds = result.findings().stream().map(finding -> finding.ruleId()).toList();
            log.info("Trial scan completed [riskLevel={}, rules={}]", result.riskLevel(), ruleIds);
            return toResponse(result, analyzedAt, locale);
        }

        UUID scanId = UUID.randomUUID();
        ScanHistory scanHistory = toHistory(scanId, ownerLicenseId, result, analyzedAt);
        historyService.save(scanHistory);

        List<String> ruleIds = scanHistory.findings().stream().map(StoredFinding::ruleId).toList();
        log.info("Scan completed [scanId={}, riskLevel={}, rules={}]", scanId, result.riskLevel(), ruleIds);

        return toResponse(scanHistory, locale);
    }

    /** Retrieves a retained scan by its opaque, canonical UUID string. */
    public ScanResponse get(String rawScanId) {
        throw new ScanNotFoundException();
    }

    /** Retrieves only a retained scan owned by {@code ownerLicenseId}. */
    public ScanResponse get(String rawScanId, UUID ownerLicenseId) {
        return get(rawScanId, ownerLicenseId, ResponseLocale.ENGLISH);
    }

    /** Retrieves a retained scan and localizes its server-owned finding copy. */
    public ScanResponse get(String rawScanId, UUID ownerLicenseId, ResponseLocale locale) {
        Objects.requireNonNull(locale, "locale");
        if (ownerLicenseId == null) {
            throw new ScanNotFoundException();
        }
        UUID scanId = ScanIdParser.parse(rawScanId);
        return historyService.findRetained(scanId, ownerLicenseId)
                .map(history -> toResponse(history, locale))
                .orElseThrow(ScanNotFoundException::new);
    }

    /** Lists private retained scan summaries for one licensed caller. */
    public ScanHistoryListResponse list(UUID ownerLicenseId, Integer limit, String cursor) {
        return list(ownerLicenseId, limit, cursor, null, null);
    }

    /** Lists private retained scan summaries with bounded server-owned filters. */
    public ScanHistoryListResponse list(
            UUID ownerLicenseId, Integer limit, String cursor, String riskLevel, String ruleId) {
        ScanHistoryPage page = riskLevel == null && ruleId == null
                ? historyService.listRetained(ownerLicenseId, limit, cursor)
                : historyService.listRetained(ownerLicenseId, limit, cursor, riskLevel, ruleId);
        List<ScanHistoryListResponse.Item> items = page.items().stream()
                .map(summary -> new ScanHistoryListResponse.Item(
                        summary.scanId(),
                        summary.redactedDisplayValue(),
                        summary.registrableDomain(),
                        summary.score(),
                        summary.riskLevel(),
                        summary.findingCount(),
                        summary.analyzedAt()))
                .toList();
        return new ScanHistoryListResponse(
                new ScanHistoryListResponse.Data(items, page.nextCursor()),
                new ScanHistoryListResponse.Meta(page.retentionDays()));
    }

    /** Maps private aggregate data to a response with backend-owned localized rule titles. */
    public ScanInsightsResponse insights(UUID ownerLicenseId, Integer windowDays, ResponseLocale locale) {
        ScanHistoryInsightsAggregate insights = historyService.insights(ownerLicenseId, windowDays);
        List<ScanInsightsResponse.TopFinding> topFindings = insights.topFindings().stream()
                .map(finding -> toTopFinding(finding, locale))
                .flatMap(Optional::stream)
                .toList();
        int resolvedWindowDays = windowDays == null ? ScanHistoryService.DEFAULT_INSIGHTS_WINDOW_DAYS : windowDays;
        return new ScanInsightsResponse(
                new ScanInsightsResponse.Data(
                        resolvedWindowDays,
                        insights.totalScans(),
                        insights.scansLast7Days(),
                        insights.previous7Days(),
                        insights.riskDistribution(),
                        topFindings),
                new ScanInsightsResponse.Meta(historyService.retentionDays()));
    }

    private static Optional<ScanInsightsResponse.TopFinding> toTopFinding(
            ScanHistoryFindingCount finding, ResponseLocale locale) {
        return FindingCopyLocalizer.localizedTitle(finding.ruleId(), locale)
                .map(title -> new ScanInsightsResponse.TopFinding(finding.ruleId(), title, finding.count()));
    }

    private ScanHistory toHistory(UUID scanId, UUID ownerLicenseId, AnalysisResult result, Instant analyzedAt) {
        var normalizedUrl = result.normalizedUrl();
        StoredNormalizedUrl normalized = new StoredNormalizedUrl(
                normalizedUrl.scheme(),
                normalizedUrl.host(),
                normalizedUrl.asciiHost(),
                normalizedUrl.registrableDomain(),
                normalizedUrl.port(),
                normalizedUrl.path(),
                normalizedUrl.queryPresent(),
                normalizedUrl.fragmentPresent());
        List<StoredFinding> findings = result.findings().stream()
                .map(finding -> new StoredFinding(
                        finding.ruleId(),
                        finding.severity(),
                        finding.points(),
                        finding.title(),
                        finding.explanation(),
                        finding.evidence()))
                .toList();
        return new ScanHistory(
                scanId,
                normalizedUrl.redactedDisplayValue(),
                normalized,
                result.score(),
                result.riskLevel(),
                findings,
                engineProperties.version(),
                analyzedAt,
                ownerLicenseId);
    }

    private ScanResponse toResponse(AnalysisResult result, Instant analyzedAt, ResponseLocale locale) {
        StoredNormalizedUrl normalized = toStoredNormalizedUrl(result);
        List<FindingResponse> findings = result.findings().stream()
                .map(finding -> toFindingResponse(
                        finding.ruleId(),
                        finding.severity(),
                        finding.points(),
                        finding.title(),
                        finding.explanation(),
                        finding.evidence(),
                        locale))
                .toList();
        return new ScanResponse(
                new ScanDataResponse(
                        null,
                        result.normalizedUrl().redactedDisplayValue(),
                        toNormalizedResponse(normalized),
                        result.score(),
                        result.riskLevel(),
                        findings,
                        analyzedAt),
                new ScanResponse.ScanMeta(engineProperties.version()));
    }

    private static StoredNormalizedUrl toStoredNormalizedUrl(AnalysisResult result) {
        var normalizedUrl = result.normalizedUrl();
        return new StoredNormalizedUrl(
                normalizedUrl.scheme(),
                normalizedUrl.host(),
                normalizedUrl.asciiHost(),
                normalizedUrl.registrableDomain(),
                normalizedUrl.port(),
                normalizedUrl.path(),
                normalizedUrl.queryPresent(),
                normalizedUrl.fragmentPresent());
    }

    private static NormalizedUrlResponse toNormalizedResponse(StoredNormalizedUrl normalized) {
        return new NormalizedUrlResponse(
                normalized.scheme(),
                normalized.host(),
                normalized.asciiHost(),
                normalized.registrableDomain(),
                normalized.port(),
                normalized.path(),
                normalized.queryPresent(),
                normalized.fragmentPresent());
    }

    private static ScanResponse toResponse(ScanHistory scanHistory, ResponseLocale locale) {
        StoredNormalizedUrl normalized = scanHistory.normalized();
        NormalizedUrlResponse normalizedResponse = new NormalizedUrlResponse(
                normalized.scheme(),
                normalized.host(),
                normalized.asciiHost(),
                normalized.registrableDomain(),
                normalized.port(),
                normalized.path(),
                normalized.queryPresent(),
                normalized.fragmentPresent());
        List<FindingResponse> findings = scanHistory.findings().stream()
                .map(finding -> toFindingResponse(
                        finding.ruleId(),
                        finding.severity(),
                        finding.points(),
                        finding.title(),
                        finding.explanation(),
                        finding.evidence(),
                        locale))
                .toList();
        ScanDataResponse data = new ScanDataResponse(
                scanHistory.scanId(),
                scanHistory.redactedDisplayValue(),
                normalizedResponse,
                scanHistory.score(),
                scanHistory.riskLevel(),
                findings,
                scanHistory.analyzedAt());
        return new ScanResponse(data, new ScanResponse.ScanMeta(scanHistory.engineVersion()));
    }

    private static FindingResponse toFindingResponse(
            String ruleId,
            Severity severity,
            int points,
            String title,
            String explanation,
            String evidence,
            ResponseLocale locale) {
        FindingCopyLocalizer.FindingCopy copy = FindingCopyLocalizer.localize(
                ruleId, title, explanation, locale);
        return new FindingResponse(ruleId, severity, points, copy.title(), copy.explanation(), evidence);
    }
}
