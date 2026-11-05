package com.lyanhkhoa.linksentry.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the rate limiter reads a request's client address from, bound from
 * {@code linksentry.client-address.*}.
 *
 * @param trustedHeader name of a header that the edge proxy overwrites with the visitor's address on
 *                      every request, such as Cloudflare's {@code CF-Connecting-IP}. Blank or absent
 *                      means buckets are keyed on the server-observed {@code getRemoteAddr()} only.
 *                      Forwarding-chain headers ({@code X-Forwarded-For}, {@code Forwarded}) are
 *                      refused at startup because clients can append to them. See
 *                      {@link ClientAddressResolver}.
 */
@ConfigurationProperties(prefix = "linksentry.client-address")
public record ClientAddressProperties(String trustedHeader) {}
