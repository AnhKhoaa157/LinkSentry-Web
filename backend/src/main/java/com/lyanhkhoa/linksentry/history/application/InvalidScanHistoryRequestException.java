package com.lyanhkhoa.linksentry.history.application;

import java.util.Map;

/** Fixed, privacy-safe validation failure for history list query parameters. */
public final class InvalidScanHistoryRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;
    private final String fieldMessage;

    private InvalidScanHistoryRequestException(String field, String message) {
        super("Invalid scan history request");
        this.field = field;
        this.fieldMessage = message;
    }

    public static InvalidScanHistoryRequestException invalidLimit() {
        return new InvalidScanHistoryRequestException("limit", "Enter a value from 1 to 50.");
    }

    public static InvalidScanHistoryRequestException invalidCursor() {
        return new InvalidScanHistoryRequestException("cursor", "Enter a valid scan history cursor.");
    }

    public static InvalidScanHistoryRequestException invalidRiskLevel() {
        return new InvalidScanHistoryRequestException("riskLevel", "Enter one of the supported risk levels.");
    }

    public static InvalidScanHistoryRequestException invalidRuleId() {
        return new InvalidScanHistoryRequestException("ruleId", "Enter a supported analysis rule.");
    }

    public static InvalidScanHistoryRequestException invalidWindowDays() {
        return new InvalidScanHistoryRequestException("windowDays", "Enter 7 or 30 days.");
    }

    public Map<String, String> fieldErrors() {
        return Map.of(field, fieldMessage);
    }
}
