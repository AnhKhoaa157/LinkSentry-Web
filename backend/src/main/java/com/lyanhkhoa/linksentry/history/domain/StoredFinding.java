package com.lyanhkhoa.linksentry.history.domain;

import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import java.util.Objects;

/** Safe, ordered finding fields that are part of a scan response. */
public record StoredFinding(
        String ruleId,
        Severity severity,
        int points,
        String title,
        String explanation,
        String evidence) {

    public StoredFinding {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(explanation, "explanation");
        if (points < 0) {
            throw new IllegalArgumentException("points must not be negative");
        }
    }
}
