package com.lyanhkhoa.linksentry.auth.application;

/** Fixed, vendor-neutral failure when the configured mail provider cannot send. */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException() {
        super("Verification email could not be sent.");
    }
}
