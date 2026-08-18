package com.lyanhkhoa.linksentry.auth.application;

/** Safe conflict raised when registration targets an existing normalized address. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("An account already exists for this email address.");
    }
}
