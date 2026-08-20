package com.lyanhkhoa.linksentry.common.ratelimit;

/** Endpoint families given independent token buckets per client address. */
enum RateLimitedRoute {
    SCAN_CREATE,
    SCAN_LOOKUP,
    DEVICE,
    EXPLANATION,
    ADMIN,
    ADMIN_AUTH_LOGIN
}
