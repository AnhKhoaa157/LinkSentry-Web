package com.lyanhkhoa.linksentry.auth.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Session lookup always filters revocation and expiry in the database. */
public interface SpringDataAuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {

    @Query("""
            select session from AuthSessionEntity session
            join fetch session.user user
            where session.tokenHash = :tokenHash
              and session.revokedAt is null
              and session.expiresAt > :now
            """)
    Optional<AuthSessionEntity> findActiveByTokenHash(
            @Param("tokenHash") String tokenHash, @Param("now") Instant now);
}
