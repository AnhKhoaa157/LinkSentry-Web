package com.lyanhkhoa.linksentry.license.domain;

/**
 * A device's entitlement as computed fresh on every status check or privileged request — never cached or
 * persisted as a column.
 *
 * <p>{@code PENDING} covers both the client-facing "Trial" and "Pending activation" wording: a device with
 * no active assignment behaves exactly like an anonymous trial caller until an administrator grants it, so
 * the two labels describe the same backend state from two moments in one UI flow.
 */
public enum DeviceState {
    PENDING,
    LICENSED,
    EXPIRED,
    REVOKED
}
