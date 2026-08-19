package com.lyanhkhoa.linksentry.auth.api;

import java.time.Instant;

/** Safe acknowledgement returned after a registration code has been sent. */
public record RegistrationStartedResponse(String message, Instant expiresAt) {}
