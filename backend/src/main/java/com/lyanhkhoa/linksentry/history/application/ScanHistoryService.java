package com.lyanhkhoa.linksentry.history.application;

import com.lyanhkhoa.linksentry.common.config.HistoryProperties;
import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryCursor;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryFilter;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryInsightsAggregate;
import com.lyanhkhoa.linksentry.history.domain.ScanHistoryRepository;
import com.lyanhkhoa.linksentry.history.domain.ScanHistorySummary;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for writing and reading retained scan snapshots. */
@Service
public class ScanHistoryService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int DEFAULT_INSIGHTS_WINDOW_DAYS = 30;

    private static final Set<String> DEFAULT_SERVER_RULE_IDS = Set.of(
            "BRAND_DOMAIN_MISMATCH",
            "BRAND_LOOKALIKE_HOSTNAME",
            "BRAND_IMPERSONATION_APK",
            "ENCODED_CHARACTERS",
            "EXCESSIVE_SUBDOMAINS",
            "EXCESSIVE_URL_LENGTH",
            "IP_LITERAL_HOST",
            "KNOWN_URL_SHORTENER",
            "MISSING_HTTPS",
            "PUNYCODE_HOST",
            "SPECIAL_USE_OR_PRIVATE_HOST",
            "SUSPICIOUS_KEYWORDS");

    private final ScanHistoryRepository repository;
    private final HistoryProperties historyProperties;
    private final Clock clock;
    private final Set<String> serverRuleIds;

    public ScanHistoryService(
            ScanHistoryRepository repository, HistoryProperties historyProperties, Clock clock) {
        this(repository, historyProperties, clock, DEFAULT_SERVER_RULE_IDS);
    }

    /** Spring wiring uses the actual configured analysis rule list as the validation catalog. */
    @Autowired
    public ScanHistoryService(
            ScanHistoryRepository repository,
            HistoryProperties historyProperties,
            Clock clock,
            List<AnalysisRule> analysisRules) {
        this(
                repository,
                historyProperties,
                clock,
                analysisRules.stream().map(AnalysisRule::id).toList());
    }

    private ScanHistoryService(
            ScanHistoryRepository repository,
            HistoryProperties historyProperties,
            Clock clock,
            Collection<String> serverRuleIds) {
        this.repository = repository;
        this.historyProperties = historyProperties;
        this.clock = clock;
        this.serverRuleIds = Set.copyOf(new LinkedHashSet<>(serverRuleIds));
    }

    /** Persists one completed scan; callers invoke this only after analysis succeeds. */
    @Transactional
    public void save(ScanHistory scanHistory) {
        Objects.requireNonNull(scanHistory.ownerLicenseId(), "ownerLicenseId");
        repository.save(scanHistory);
    }

    /** Returns a scan only when it has not crossed the configured retention boundary. */
    @Transactional(readOnly = true)
    public Optional<ScanHistory> findRetained(UUID scanId, UUID ownerLicenseId) {
        if (ownerLicenseId == null) {
            return Optional.empty();
        }
        return repository.findRetained(scanId, ownerLicenseId, retainedSince());
    }

    /** Returns one private, retention-aware page for the caller's license. */
    @Transactional(readOnly = true)
    public ScanHistoryPage listRetained(UUID ownerLicenseId, Integer requestedLimit, String rawCursor) {
        return listRetained(ownerLicenseId, requestedLimit, rawCursor, null, null);
    }

    /** Returns one private page after validating all optional bounded filters. */
    @Transactional(readOnly = true)
    public ScanHistoryPage listRetained(
            UUID ownerLicenseId,
            Integer requestedLimit,
            String rawCursor,
            String rawRiskLevel,
            String rawRuleId) {
        int pageSize = requestedLimit == null ? DEFAULT_PAGE_SIZE : requestedLimit;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw InvalidScanHistoryRequestException.invalidLimit();
        }
        ScanHistoryFilter filter = parseFilter(rawRiskLevel, rawRuleId);
        if (ownerLicenseId == null) {
            return new ScanHistoryPage(List.of(), null, historyProperties.retentionDays());
        }

        Optional<ScanHistoryCursor> cursor = rawCursor == null
                ? Optional.empty()
                : Optional.of(ScanHistoryCursorCodec.decode(rawCursor));
        List<ScanHistorySummary> candidates = filter.equals(ScanHistoryFilter.none())
                ? repository.findRetainedPage(ownerLicenseId, retainedSince(), cursor, pageSize + 1)
                : repository.findRetainedPage(ownerLicenseId, retainedSince(), filter, cursor, pageSize + 1);
        boolean hasNextPage = candidates.size() > pageSize;
        List<ScanHistorySummary> items = hasNextPage
                ? List.copyOf(candidates.subList(0, pageSize))
                : List.copyOf(candidates);
        String nextCursor = hasNextPage ? cursorFor(items.getLast()) : null;
        return new ScanHistoryPage(items, nextCursor, historyProperties.retentionDays());
    }

    /** Returns retained aggregate counts for the caller's license and requested window. */
    @Transactional(readOnly = true)
    public ScanHistoryInsightsAggregate insights(UUID ownerLicenseId, Integer requestedWindowDays) {
        int windowDays = requestedWindowDays == null ? DEFAULT_INSIGHTS_WINDOW_DAYS : requestedWindowDays;
        if (windowDays != 7 && windowDays != 30) {
            throw InvalidScanHistoryRequestException.invalidWindowDays();
        }
        Instant now = Instant.now(clock);
        if (ownerLicenseId == null) {
            return emptyInsights();
        }
        return repository.findRetainedInsights(
                ownerLicenseId,
                retainedSince(now),
                now,
                now.minus(windowDays, ChronoUnit.DAYS),
                now.minus(7, ChronoUnit.DAYS),
                now.minus(14, ChronoUnit.DAYS),
                serverRuleIds);
    }

    public int retentionDays() {
        return historyProperties.retentionDays();
    }

    private ScanHistoryFilter parseFilter(String rawRiskLevel, String rawRuleId) {
        Optional<RiskLevel> riskLevel = parseRiskLevel(rawRiskLevel);
        Optional<String> ruleId = parseRuleId(rawRuleId);
        return new ScanHistoryFilter(riskLevel, ruleId);
    }

    private static Optional<RiskLevel> parseRiskLevel(String rawRiskLevel) {
        if (rawRiskLevel == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(RiskLevel.valueOf(rawRiskLevel));
        } catch (IllegalArgumentException exception) {
            throw InvalidScanHistoryRequestException.invalidRiskLevel();
        }
    }

    private Optional<String> parseRuleId(String rawRuleId) {
        if (rawRuleId == null) {
            return Optional.empty();
        }
        if (rawRuleId.isBlank() || rawRuleId.length() > 64 || !serverRuleIds.contains(rawRuleId)) {
            throw InvalidScanHistoryRequestException.invalidRuleId();
        }
        return Optional.of(rawRuleId);
    }

    private ScanHistoryInsightsAggregate emptyInsights() {
        return new ScanHistoryInsightsAggregate(
                0,
                0,
                0,
                java.util.Map.of(
                        RiskLevel.LOW, 0L,
                        RiskLevel.MODERATE, 0L,
                        RiskLevel.HIGH, 0L,
                        RiskLevel.CRITICAL, 0L),
                List.of());
    }

    private static String cursorFor(ScanHistorySummary summary) {
        return ScanHistoryCursorCodec.encode(new ScanHistoryCursor(summary.analyzedAt(), summary.scanId()));
    }

    private Instant retainedSince() {
        return retainedSince(Instant.now(clock));
    }

    private Instant retainedSince(Instant now) {
        return now.minus(historyProperties.retentionDays(), ChronoUnit.DAYS);
    }
}
