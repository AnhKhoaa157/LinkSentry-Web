package com.lyanhkhoa.linksentry.scan.application;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisResult;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import com.lyanhkhoa.linksentry.common.config.EngineProperties;
import com.lyanhkhoa.linksentry.history.application.ScanHistoryService;
import com.lyanhkhoa.linksentry.history.application.ScanNotFoundException;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.StoredFinding;
import com.lyanhkhoa.linksentry.history.domain.StoredNormalizedUrl;
import com.lyanhkhoa.linksentry.scan.api.FindingResponse;
import com.lyanhkhoa.linksentry.scan.api.NormalizedUrlResponse;
import com.lyanhkhoa.linksentry.scan.api.ScanDataResponse;
import com.lyanhkhoa.linksentry.scan.api.ScanResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
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

    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

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
        return scan(rawInput, null);
    }

    /**
     * Analyses one URL. Anonymous requests receive the safe result in memory and
     * are deliberately neither assigned a retrievable ID nor persisted.
     */
    public ScanResponse scan(String rawInput, UUID ownerUserId) {
        AnalysisResult result = urlAnalyzer.analyze(rawInput);

        Instant analyzedAt = Instant.now(clock);
        if (ownerUserId == null) {
            List<String> ruleIds = result.findings().stream().map(finding -> finding.ruleId()).toList();
            log.info("Anonymous scan completed [riskLevel={}, rules={}]", result.riskLevel(), ruleIds);
            return toResponse(result, analyzedAt);
        }

        UUID scanId = UUID.randomUUID();
        ScanHistory scanHistory = toHistory(scanId, ownerUserId, result, analyzedAt);
        historyService.save(scanHistory);

        List<String> ruleIds = scanHistory.findings().stream().map(StoredFinding::ruleId).toList();
        log.info("Scan completed [scanId={}, riskLevel={}, rules={}]", scanId, result.riskLevel(), ruleIds);

        return toResponse(scanHistory);
    }

    /** Retrieves a retained scan by its opaque, canonical UUID string. */
    public ScanResponse get(String rawScanId) {
        throw new ScanNotFoundException();
    }

    /** Retrieves only a retained scan owned by {@code ownerUserId}. */
    public ScanResponse get(String rawScanId, UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new ScanNotFoundException();
        }
        UUID scanId = parseScanId(rawScanId);
        return historyService.findRetained(scanId, ownerUserId)
                .map(ScanService::toResponse)
                .orElseThrow(ScanNotFoundException::new);
    }

    private ScanHistory toHistory(UUID scanId, UUID ownerUserId, AnalysisResult result, Instant analyzedAt) {
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
                ownerUserId);
    }

    private ScanResponse toResponse(AnalysisResult result, Instant analyzedAt) {
        StoredNormalizedUrl normalized = toStoredNormalizedUrl(result);
        List<FindingResponse> findings = result.findings().stream()
                .map(finding -> new FindingResponse(
                        finding.ruleId(),
                        finding.severity(),
                        finding.points(),
                        finding.title(),
                        finding.explanation(),
                        finding.evidence()))
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

    private static ScanResponse toResponse(ScanHistory scanHistory) {
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
                .map(finding -> new FindingResponse(
                        finding.ruleId(),
                        finding.severity(),
                        finding.points(),
                        finding.title(),
                        finding.explanation(),
                        finding.evidence()))
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

    private UUID parseScanId(String rawScanId) {
        if (rawScanId == null || !CANONICAL_UUID.matcher(rawScanId).matches()) {
            throw new ScanNotFoundException();
        }
        try {
            return UUID.fromString(rawScanId);
        } catch (IllegalArgumentException exception) {
            // Do not retain or expose a malformed path value in an exception cause.
            throw new ScanNotFoundException();
        }
    }
}
