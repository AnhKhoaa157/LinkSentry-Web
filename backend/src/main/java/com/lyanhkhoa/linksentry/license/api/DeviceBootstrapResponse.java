package com.lyanhkhoa.linksentry.license.api;

import java.util.UUID;

/**
 * Response of {@code POST /api/v1/devices}, returned exactly once.
 *
 * @param credential the raw device credential; the server retains only its hash, so this is the caller's
 *                    only opportunity to see it. The client must store it and never render it again.
 */
public record DeviceBootstrapResponse(UUID deviceId, String activationCode, String credential) {}
