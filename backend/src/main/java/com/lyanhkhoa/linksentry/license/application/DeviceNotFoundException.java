package com.lyanhkhoa.linksentry.license.application;

/** Safe not-found for an admin request naming an unknown device or activation code. */
public final class DeviceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DeviceNotFoundException() {
        super("The requested device was not found.");
    }
}
