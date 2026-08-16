/**
 * Single-instance, in-memory token-bucket rate limiting for the two scan endpoints.
 *
 * <p>Buckets are keyed only by {@link jakarta.servlet.http.HttpServletRequest#getRemoteAddr()};
 * client-supplied forwarding headers are never consulted. There is no shared or
 * distributed state — see {@code docs/ARCHITECTURE.md} for what that implies behind
 * a load balancer.
 */
package com.lyanhkhoa.linksentry.common.ratelimit;
