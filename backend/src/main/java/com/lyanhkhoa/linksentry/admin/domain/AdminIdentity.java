package com.lyanhkhoa.linksentry.admin.domain;

import java.time.Instant;
import java.util.UUID;

/** Identity installed by {@code admin.security.AdminSessionAuthenticationFilter}. Contains no raw secret. */
public record AdminIdentity(UUID adminUserId, String username, UUID sessionId, Instant expiresAt) {}
