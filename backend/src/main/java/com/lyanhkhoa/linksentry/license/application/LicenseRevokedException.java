package com.lyanhkhoa.linksentry.license.application;

/** Safe conflict when granting a device against a license that is already revoked. */
public final class LicenseRevokedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LicenseRevokedException() {
        super("This license has been revoked and cannot accept new devices.");
    }
}
