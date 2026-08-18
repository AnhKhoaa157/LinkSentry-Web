package com.lyanhkhoa.linksentry.history.application;

/** Safe application error for missing, malformed, expired, ownerless, or cross-user scan IDs. */
public final class ScanNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ScanNotFoundException() {
        super("The requested scan was not found.");
    }
}
