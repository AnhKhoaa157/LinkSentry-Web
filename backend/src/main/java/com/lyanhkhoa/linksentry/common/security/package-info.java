/**
 * Security configuration.
 *
 * <p>Holds the stateless Spring Security filter chain and the CORS source. Retained
 * history and the AI explanation route require a currently licensed device
 * ({@link com.lyanhkhoa.linksentry.common.security.DeviceAuthenticationFilter}); admin routes require
 * either a {@code ROLE_ADMIN} session or {@code ADMIN_API_KEY}
 * ({@link com.lyanhkhoa.linksentry.common.security.AdminApiKeyFilter}); one-off scan
 * POSTs and device bootstrap/status remain anonymous. See {@code docs/SECURITY_BOUNDARY.md} for the
 * boundary.
 */
package com.lyanhkhoa.linksentry.common.security;
