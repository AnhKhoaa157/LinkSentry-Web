package com.lyanhkhoa.linksentry.auth.api;

import java.time.Instant;

/** Safe current-session response; it deliberately contains no bearer token. */
public record SessionResponse(Instant expiresAt, AuthUserResponse user) {}
