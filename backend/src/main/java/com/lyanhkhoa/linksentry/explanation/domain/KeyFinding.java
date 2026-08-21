package com.lyanhkhoa.linksentry.explanation.domain;

import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import java.util.Objects;

/**
 * One deterministically-selected, user-safe finding surfaced in an explanation
 * response. Assembled by {@code ExplanationService} directly from the retained
 * scan's {@code StoredFinding} list — never produced or altered by the AI
 * provider — and carries only the fields already safe to show a caller:
 * {@code title}, the rule's own generic {@code explanation}, {@code severity},
 * and {@code points}. Never {@code ruleId} or {@code evidence}.
 */
public record KeyFinding(String title, String explanation, Severity severity, int points) {

    public KeyFinding {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(explanation, "explanation");
        Objects.requireNonNull(severity, "severity");
        if (points < 0) {
            throw new IllegalArgumentException("points must not be negative");
        }
    }
}
