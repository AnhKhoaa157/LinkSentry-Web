package com.lyanhkhoa.linksentry.admin.api;

import java.time.Instant;

/** Safe current-session response; it deliberately contains no bearer token. */
public record AdminSessionResponse(Instant expiresAt, AdminIdentityResponse admin) {}
