package com.lyanhkhoa.linksentry.scan.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lyanhkhoa.linksentry.common.api.ErrorResponse;
import java.util.List;

/** Per-item outcomes from {@code POST /api/v1/scans/batch}. */
public record BatchScanResponse(Data data, Meta meta) {

    public record Data(List<Item> results) {}

    /**
     * @param index zero-based index in the submitted {@code urls} list
     * @param status whether this item completed, failed, or was skipped after a batch-wide stop
     * @param result the safe scan result, present only when {@code status} is {@code COMPLETE}
     * @param error the standard safe error envelope, present only when this item failed
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(int index, Status status, ScanDataResponse result, ErrorResponse error) {}

    public enum Status {
        COMPLETE,
        FAILED,
        SKIPPED
    }

    public record Meta(String engineVersion) {}
}
