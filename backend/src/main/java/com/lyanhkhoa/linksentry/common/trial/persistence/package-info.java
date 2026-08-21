/**
 * Persistence for the device-scoped trial quota (ADR 0010): one row per admitted trial scan,
 * counted and pruned under a {@code SELECT ... FOR UPDATE} lock on the parent
 * {@code device_installation} row, the same locking shape {@code license.application
 * .LicenseAdminService#grantDevice} already uses against the license row.
 *
 * <p>This package owns {@code device_trial_scan_event} while FK-referencing {@code
 * license.domain.DeviceRepository}'s primary key — the same shape {@code history.persistence}
 * already uses to own {@code scan_history} while FK-referencing {@code license}, so a feature
 * package owning a table that references another feature's primary key is precedented here, not
 * novel.
 */
package com.lyanhkhoa.linksentry.common.trial.persistence;
