package com.lyanhkhoa.linksentry.scan.api;

/**
 * Top-level envelope of {@code POST /api/v1/scans} and
 * {@code GET /api/v1/scans/{scanId}}.
 *
 * @param data the scan result
 * @param meta metadata about how the result was produced
 */
public record ScanResponse(ScanDataResponse data, ScanMeta meta) {

    /** @param engineVersion the analysis engine version that produced {@code data} */
    public record ScanMeta(String engineVersion) {}
}
