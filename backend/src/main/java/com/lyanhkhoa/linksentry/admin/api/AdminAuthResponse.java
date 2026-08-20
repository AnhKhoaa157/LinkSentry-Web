package com.lyanhkhoa.linksentry.admin.api;

import java.time.Instant;

/** Login response. The bearer value is returned only by this call. */
public record AdminAuthResponse(String accessToken, String tokenType, Instant expiresAt, AdminIdentityResponse admin) {}
