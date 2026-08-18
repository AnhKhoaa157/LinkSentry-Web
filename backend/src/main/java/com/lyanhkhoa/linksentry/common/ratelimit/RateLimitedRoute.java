package com.lyanhkhoa.linksentry.common.ratelimit;

/** The two endpoints given an independent token bucket per client address. */
enum RateLimitedRoute {
    SCAN_CREATE,
    SCAN_LOOKUP
}
