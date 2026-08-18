package com.lyanhkhoa.linksentry.auth.security;

import java.time.Instant;
import java.util.UUID;

/** Identity installed by the server-side bearer-token filter. It contains no raw secret. */
public record AuthenticatedUser(UUID userId, String email, UUID sessionId, Instant expiresAt) {}
