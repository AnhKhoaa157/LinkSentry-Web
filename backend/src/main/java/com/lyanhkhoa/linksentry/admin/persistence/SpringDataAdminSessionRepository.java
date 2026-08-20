package com.lyanhkhoa.linksentry.admin.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Session lookup always filters revocation and expiry in the database. */
public interface SpringDataAdminSessionRepository extends JpaRepository<AdminSessionEntity, UUID> {

    @Query("""
            select session from AdminSessionEntity session
            join fetch session.adminUser adminUser
            where session.tokenHash = :tokenHash
              and session.revokedAt is null
              and session.expiresAt > :now
            """)
    Optional<AdminSessionEntity> findActiveByTokenHash(
            @Param("tokenHash") String tokenHash, @Param("now") Instant now);
}
