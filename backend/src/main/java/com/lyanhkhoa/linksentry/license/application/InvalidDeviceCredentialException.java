package com.lyanhkhoa.linksentry.license.application;

/** Safe rejection for a missing, malformed, or unrecognised device credential. */
public final class InvalidDeviceCredentialException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidDeviceCredentialException() {
        super("The device credential is missing or invalid.");
    }
}
