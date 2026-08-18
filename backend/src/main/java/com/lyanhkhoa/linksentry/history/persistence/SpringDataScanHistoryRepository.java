package com.lyanhkhoa.linksentry.history.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data adapter contract; callers depend on the history domain port instead. */
public interface SpringDataScanHistoryRepository extends JpaRepository<ScanHistoryEntity, UUID> {

    @Query("""
            select scan from ScanHistoryEntity scan
            where scan.scanId = :scanId
              and scan.ownerUserId = :ownerUserId
              and scan.analyzedAt >= :retainedSince
            """)
    Optional<ScanHistoryEntity> findRetained(
            @Param("scanId") UUID scanId,
            @Param("ownerUserId") UUID ownerUserId,
            @Param("retainedSince") Instant retainedSince);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ScanHistoryEntity scan where scan.analyzedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
