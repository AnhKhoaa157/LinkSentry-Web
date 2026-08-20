package com.lyanhkhoa.linksentry.license.application;

/** Safe conflict when granting a device would exceed a license's configured device cap. */
public final class DeviceLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DeviceLimitExceededException() {
        super("This license already has the maximum number of active devices.");
    }
}
