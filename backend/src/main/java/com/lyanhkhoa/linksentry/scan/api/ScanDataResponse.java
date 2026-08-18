package com.lyanhkhoa.linksentry.scan.api;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The {@code data} payload of a successful scan response.
 *
 * @param scanId     owner-only retained-result identifier; null for anonymous scans
 * @param input      the redacted display value — never the raw submission
 * @param normalized the analysed URL's public fields
 * @param score      total risk score, clamped to {@code 0..100}
 * @param riskLevel  band {@code score} falls into
 * @param findings   every finding produced, ordered deterministically
 * @param analyzedAt when the scan was performed, in UTC
 */
public record ScanDataResponse(
        UUID scanId,
        String input,
        NormalizedUrlResponse normalized,
        int score,
        RiskLevel riskLevel,
        List<FindingResponse> findings,
        Instant analyzedAt) {}
