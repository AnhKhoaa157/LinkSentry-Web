package com.lyanhkhoa.linksentry.scan.application;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisResult;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import com.lyanhkhoa.linksentry.common.config.EngineProperties;
import com.lyanhkhoa.linksentry.scan.api.FindingResponse;
import com.lyanhkhoa.linksentry.scan.api.NormalizedUrlResponse;
import com.lyanhkhoa.linksentry.scan.api.ScanDataResponse;
import com.lyanhkhoa.linksentry.scan.api.ScanResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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

    public ScanService(UrlAnalyzer urlAnalyzer, EngineProperties engineProperties, Clock clock) {
        this.urlAnalyzer = urlAnalyzer;
        this.engineProperties = engineProperties;
        this.clock = clock;
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
        AnalysisResult result = urlAnalyzer.analyze(rawInput);

        UUID scanId = UUID.randomUUID();
        Instant analyzedAt = Instant.now(clock);

        List<FindingResponse> findings =
                result.findings().stream().map(FindingResponse::from).toList();
        List<String> ruleIds = result.findings().stream().map(RuleFinding::ruleId).toList();
        log.info("Scan completed [scanId={}, riskLevel={}, rules={}]", scanId, result.riskLevel(), ruleIds);

        ScanDataResponse data = new ScanDataResponse(
                scanId,
                result.normalizedUrl().redactedDisplayValue(),
                NormalizedUrlResponse.from(result.normalizedUrl()),
                result.score(),
                result.riskLevel(),
                findings,
                analyzedAt);

        return new ScanResponse(data, new ScanResponse.ScanMeta(engineProperties.version()));
    }
}
