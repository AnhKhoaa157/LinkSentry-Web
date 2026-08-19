package com.lyanhkhoa.linksentry.auth.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence operations for short-lived pending registrations. */
public interface SpringDataRegistrationVerificationRepository
        extends JpaRepository<RegistrationVerificationEntity, String> {

    long deleteByExpiresAtBefore(Instant now);
}
