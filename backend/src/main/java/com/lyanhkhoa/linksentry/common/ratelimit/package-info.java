/**
 * Single-instance, in-memory token-bucket rate limiting for scan and auth endpoints.
 *
 * <p>Buckets are keyed by the client address {@link ClientAddressResolver} picks: the
 * server-observed {@link jakarta.servlet.http.HttpServletRequest#getRemoteAddr()}, or, when
 * {@code linksentry.client-address.trusted-header} is set, the single IP literal that the edge
 * proxy writes into that header. Forwarding-chain headers ({@code X-Forwarded-For},
 * {@code Forwarded}) are never read. There is no shared or distributed state, so each instance
 * enforces its own buckets.
 */
package com.lyanhkhoa.linksentry.common.ratelimit;
