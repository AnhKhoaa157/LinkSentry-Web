package com.lyanhkhoa.linksentry.history.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Stable keyset position for retained-history pagination. */
public record ScanHistoryCursor(Instant analyzedAt, UUID scanId) {

    public ScanHistoryCursor {
        Objects.requireNonNull(analyzedAt, "analyzedAt");
        Objects.requireNonNull(scanId, "scanId");
    }
}
