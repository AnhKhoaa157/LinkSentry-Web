package com.lyanhkhoa.linksentry.auth.application;

/** Deliberately non-enumerating failure for missing, wrong, expired, or exhausted codes. */
public class InvalidVerificationCodeException extends RuntimeException {

    public InvalidVerificationCodeException() {
        super("The verification code is invalid or expired.");
    }
}
