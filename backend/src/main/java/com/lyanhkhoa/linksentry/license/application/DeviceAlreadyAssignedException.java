package com.lyanhkhoa.linksentry.license.application;

/** Safe conflict when granting a device that already holds an active assignment to some license. */
public final class DeviceAlreadyAssignedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DeviceAlreadyAssignedException() {
        super("This device is already granted to a license. Revoke it first to move it to another license.");
    }
}
