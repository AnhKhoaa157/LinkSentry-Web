package com.lyanhkhoa.linksentry.history.persistence;

import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.history.domain.ScanHistory;
import com.lyanhkhoa.linksentry.history.domain.StoredFinding;
import com.lyanhkhoa.linksentry.history.domain.StoredNormalizedUrl;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** JPA representation of one safe scan snapshot. */
@Entity
@Table(name = "scan_history")
public class ScanHistoryEntity {

    @Id
    @Column(name = "scan_id", nullable = false, columnDefinition = "uuid")
    private UUID scanId;

    @Column(name = "redacted_display_value", nullable = false, length = 2048)
    private String redactedDisplayValue;

    @Column(name = "scheme", nullable = false, length = 5)
    private String scheme;

    @Column(name = "host", nullable = false, length = 512)
    private String host;

    @Column(name = "ascii_host", nullable = false, length = 253)
    private String asciiHost;

    @Column(name = "registrable_domain", length = 253)
    private String registrableDomain;

    @Column(name = "port")
    private Integer port;

    @Column(name = "path", nullable = false, length = 2048)
    private String path;

    @Column(name = "query_present", nullable = false)
    private boolean queryPresent;

    @Column(name = "fragment_present", nullable = false)
    private boolean fragmentPresent;

    @Column(name = "score", nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private RiskLevel riskLevel;

    @Column(name = "engine_version", nullable = false, length = 128)
    private String engineVersion;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    @OneToMany(mappedBy = "scanHistory", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("findingPosition ASC")
    private List<ScanHistoryFindingEntity> findings = new ArrayList<>();

    protected ScanHistoryEntity() {}

    public ScanHistoryEntity(ScanHistory scanHistory) {
        this.scanId = scanHistory.scanId();
        this.redactedDisplayValue = scanHistory.redactedDisplayValue();
        StoredNormalizedUrl normalized = scanHistory.normalized();
        this.scheme = normalized.scheme();
        this.host = normalized.host();
        this.asciiHost = normalized.asciiHost();
        this.registrableDomain = normalized.registrableDomain();
        this.port = normalized.port();
        this.path = normalized.path();
        this.queryPresent = normalized.queryPresent();
        this.fragmentPresent = normalized.fragmentPresent();
        this.score = scanHistory.score();
        this.riskLevel = scanHistory.riskLevel();
        this.engineVersion = scanHistory.engineVersion();
        this.analyzedAt = scanHistory.analyzedAt();
    }

    public void addFinding(int position, StoredFinding finding) {
        this.findings.add(new ScanHistoryFindingEntity(this, position, finding));
    }

    public ScanHistory toDomain() {
        StoredNormalizedUrl normalized = new StoredNormalizedUrl(
                scheme, host, asciiHost, registrableDomain, port, path, queryPresent, fragmentPresent);
        List<StoredFinding> storedFindings = findings.stream()
                .map(ScanHistoryFindingEntity::toDomain)
                .toList();
        return new ScanHistory(
                scanId,
                redactedDisplayValue,
                normalized,
                score,
                riskLevel,
                storedFindings,
                engineVersion,
                analyzedAt);
    }

    public UUID getScanId() {
        return scanId;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }
}
