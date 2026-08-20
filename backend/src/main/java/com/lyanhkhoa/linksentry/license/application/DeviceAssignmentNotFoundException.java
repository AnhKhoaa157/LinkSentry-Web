package com.lyanhkhoa.linksentry.license.application;

/** Safe not-found when revoking a device that has no currently active license assignment. */
public final class DeviceAssignmentNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DeviceAssignmentNotFoundException() {
        super("This device has no active license assignment to revoke.");
    }
}
