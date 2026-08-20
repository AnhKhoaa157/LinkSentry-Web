package com.lyanhkhoa.linksentry.admin.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA row for an administrator account; no raw password is represented here. */
@Entity
@Table(name = "admin_user")
public class AdminUserEntity {

    @Id
    @Column(name = "admin_user_id", nullable = false, columnDefinition = "uuid")
    private UUID adminUserId;

    @Column(name = "username", nullable = false, length = 120, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AdminUserEntity() {}

    public AdminUserEntity(UUID adminUserId, String username, String passwordHash, Instant createdAt) {
        this.adminUserId = adminUserId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public UUID getAdminUserId() {
        return adminUserId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
