package com.lyanhkhoa.linksentry.auth.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Account persistence operations used by the auth application service. */
public interface SpringDataUserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {

    boolean existsByEmail(String email);

    Optional<UserAccountEntity> findByEmail(String email);
}
