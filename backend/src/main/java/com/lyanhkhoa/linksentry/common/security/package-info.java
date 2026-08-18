/**
 * Security configuration.
 *
 * <p>Holds the stateless bearer Spring Security filter chain and the CORS source.
 * Retained history and account/session routes are protected; one-off scan POSTs
 * remain anonymous. See {@code docs/SECURITY_BOUNDARY.md} for the boundary.
 */
package com.lyanhkhoa.linksentry.common.security;
