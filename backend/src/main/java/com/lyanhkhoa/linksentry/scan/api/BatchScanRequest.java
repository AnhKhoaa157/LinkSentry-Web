package com.lyanhkhoa.linksentry.scan.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request body for {@code POST /api/v1/scans/batch}. */
public record BatchScanRequest(
        @NotEmpty(message = "Enter between 1 and 10 URLs.")
                @Size(max = 10, message = "Enter no more than 10 URLs.")
                List<@NotNull(message = "Enter a URL.") String> urls) {}
