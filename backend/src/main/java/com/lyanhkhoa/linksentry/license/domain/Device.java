package com.lyanhkhoa.linksentry.license.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One independent web or extension installation.
 *
 * <p>{@code activationCode} is public: safe to display, copy, and send to an administrator. {@code
 * credentialHash} is the only representation ever persisted of the client's secret device credential;
 * the raw credential exists only in the client and in the single bootstrap response that created it.
 */
public record Device(UUID deviceId, String activationCode, String credentialHash, String clientLabel, Instant createdAt) {

    public Device {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(activationCode, "activationCode");
        Objects.requireNonNull(credentialHash, "credentialHash");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
