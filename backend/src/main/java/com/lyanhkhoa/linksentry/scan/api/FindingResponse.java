package com.lyanhkhoa.linksentry.scan.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;

/**
 * Wire representation of one {@link RuleFinding}.
 *
 * @param ruleId      stable machine-readable identifier of the rule that fired
 * @param severity    qualitative weight
 * @param points      explicit, non-negative contribution to the risk score
 * @param title       short, non-technical label
 * @param explanation one or two sentences a non-expert can act on
 * @param evidence    optional redacted supporting detail; omitted from JSON when absent
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FindingResponse(
        String ruleId, Severity severity, int points, String title, String explanation, String evidence) {

    public static FindingResponse from(RuleFinding finding) {
        return new FindingResponse(
                finding.ruleId(), finding.severity(), finding.points(), finding.title(), finding.explanation(),
                finding.evidence());
    }
}
