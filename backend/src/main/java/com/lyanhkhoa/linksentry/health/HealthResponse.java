package com.lyanhkhoa.linksentry.health;

/**
 * Response of {@code GET /api/v1/health}.
 *
 * <p>Intentionally minimal. This endpoint exists so the frontend shell can show
 * whether the API is reachable; it must not disclose versions, dependency status,
 * or anything else useful to someone fingerprinting the service.
 *
 * @param status  always {@code "UP"} — if the application cannot serve this, the
 *                request fails at the transport level instead
 * @param service stable service identifier
 */
public record HealthResponse(String status, String service) {}
