package com.lyanhkhoa.linksentry.scan.api;

import com.lyanhkhoa.linksentry.scan.application.ScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for {@code POST /api/v1/scans} and retained scan retrieval.
 *
 * <p>Validates, delegates to {@link ScanService}, and returns its result. No
 * analysis logic lives here — a malformed or unsupported URL results in a
 * {@code 400 INVALID_URL} response via
 * {@link com.lyanhkhoa.linksentry.common.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/scans")
@Tag(name = "Scans", description = "URL risk analysis and retained results")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping
    @Operation(summary = "Analyse a URL and return its explainable risk score")
    public ScanResponse scan(@Valid @RequestBody ScanRequest request) {
        return scanService.scan(request.url());
    }

    @GetMapping("/{scanId}")
    @Operation(summary = "Retrieve a retained scan result by opaque scan ID")
    public ScanResponse get(@PathVariable String scanId) {
        return scanService.get(scanId);
    }
}
