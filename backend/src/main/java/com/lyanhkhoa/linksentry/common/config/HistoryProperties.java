package com.lyanhkhoa.linksentry.common.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Retention policy for owner-bound scan history snapshots. */
@Validated
@ConfigurationProperties(prefix = "linksentry.history")
public record HistoryProperties(@Min(1) int retentionDays) {
}
