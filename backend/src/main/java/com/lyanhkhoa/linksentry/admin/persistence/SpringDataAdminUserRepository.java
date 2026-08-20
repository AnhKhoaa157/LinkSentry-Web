package com.lyanhkhoa.linksentry.admin.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Administrator account persistence operations used by the admin application service. */
public interface SpringDataAdminUserRepository extends JpaRepository<AdminUserEntity, UUID> {

    Optional<AdminUserEntity> findByUsername(String username);
}
