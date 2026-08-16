package com.lyanhkhoa.linksentry.scan.api;

import com.lyanhkhoa.linksentry.analysis.normalization.UrlNormalizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/v1/scans}.
 *
 * <p>Bean Validation only enforces what is cheap to check before parsing —
 * presence and length. Whether {@code url} is a well-formed {@code http}/{@code
 * https} address is the {@link UrlNormalizer}'s job, and a failure there is
 * reported as {@code INVALID_URL}, not a validation error.
 *
 * @param url the submitted address, required, non-blank, at most {@link
 *            UrlNormalizer#MAX_URL_LENGTH} characters
 */
public record ScanRequest(
        @NotBlank(message = "Enter a valid HTTP or HTTPS URL.")
                @Size(max = UrlNormalizer.MAX_URL_LENGTH, message = "Enter a valid HTTP or HTTPS URL.")
                String url) {}
