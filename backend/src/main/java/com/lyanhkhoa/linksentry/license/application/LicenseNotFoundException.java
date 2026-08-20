package com.lyanhkhoa.linksentry.license.application;

/** Safe not-found for an admin request naming an unknown license ID. */
public final class LicenseNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LicenseNotFoundException() {
        super("The requested license was not found.");
    }
}
