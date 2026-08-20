package com.lyanhkhoa.linksentry.license.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.HttpHeaders;

/**
 * Reads the {@code Authorization: Device <credential>} scheme, shared by {@code
 * common.security.DeviceAuthenticationFilter} (privileged requests) and {@code
 * license.api.DeviceController#me} (status checks for a device in any state, licensed or not).
 */
public final class DeviceCredentialHeader {

    private static final String DEVICE_PREFIX = "Device ";
    private static final int MAX_CREDENTIAL_LENGTH = 256;

    private DeviceCredentialHeader() {}

    /** Returns the trimmed raw credential, or empty when the header is absent, malformed, or oversized. */
    public static Optional<String> read(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(DEVICE_PREFIX)) {
            return Optional.empty();
        }
        String rawCredential = authorization.substring(DEVICE_PREFIX.length()).trim();
        if (rawCredential.isEmpty() || rawCredential.length() > MAX_CREDENTIAL_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(rawCredential);
    }
}
