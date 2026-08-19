package com.lyanhkhoa.linksentry.auth.application;

import java.time.Duration;

/** Outbound port for sending a registration verification code. */
@FunctionalInterface
public interface RegistrationCodeSender {

    void send(String email, String code, Duration ttl);
}
