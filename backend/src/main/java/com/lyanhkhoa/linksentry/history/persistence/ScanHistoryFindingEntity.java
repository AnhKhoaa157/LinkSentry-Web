package com.lyanhkhoa.linksentry.history.persistence;

import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import com.lyanhkhoa.linksentry.history.domain.StoredFinding;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Ordered child row for one persisted scan finding. */
@Entity
@Table(name = "scan_history_finding")
public class ScanHistoryFindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false)
    private ScanHistoryEntity scanHistory;

    @Column(name = "finding_position", nullable = false)
    private int findingPosition;

    @Column(name = "rule_id", nullable = false, length = 64)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private Severity severity;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "explanation", nullable = false, length = 2048)
    private String explanation;

    @Column(name = "evidence", length = 2048)
    private String evidence;

    protected ScanHistoryFindingEntity() {}

    public ScanHistoryFindingEntity(ScanHistoryEntity scanHistory, int findingPosition, StoredFinding finding) {
        this.scanHistory = scanHistory;
        this.findingPosition = findingPosition;
        this.ruleId = finding.ruleId();
        this.severity = finding.severity();
        this.points = finding.points();
        this.title = finding.title();
        this.explanation = finding.explanation();
        this.evidence = finding.evidence();
    }

    public int getFindingPosition() {
        return findingPosition;
    }

    public StoredFinding toDomain() {
        return new StoredFinding(ruleId, severity, points, title, explanation, evidence);
    }
}
