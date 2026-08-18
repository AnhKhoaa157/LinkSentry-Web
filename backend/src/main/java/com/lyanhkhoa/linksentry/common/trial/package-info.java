/**
 * Single-instance, in-memory rolling-window trial quota for anonymous
 * {@code POST /api/v1/scans} requests, independent of {@code common.ratelimit}'s
 * general anti-abuse control.
 *
 * <p>Identity comes only from {@link jakarta.servlet.http.HttpServletRequest#getRemoteAddr()};
 * client-supplied forwarding headers are never consulted. There is no shared or
 * distributed state — see {@code docs/ARCHITECTURE.md} for what that implies behind
 * a load balancer, and {@code docs/SECURITY_BOUNDARY.md} for the exact-address
 * identity caveat this control shares with the rate limiter.
 */
package com.lyanhkhoa.linksentry.common.trial;
