package com.lyanhkhoa.linksentry.auth.api;

import java.time.Instant;

/** Registration/login response. The bearer value is returned only on these calls. */
public record AuthResponse(
        String accessToken, String tokenType, Instant expiresAt, AuthUserResponse user) {}
