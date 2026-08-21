/**
 * Persistent, device-scoped rolling-window trial quota for anonymous {@code POST /api/v1/scans}
 * requests (ADR 0010), independent of {@code common.ratelimit}'s general anti-abuse control.
 *
 * <p>Identity is the {@code device_id} a presented {@code Authorization: Device <credential>}
 * header resolves to — pending, expired, or revoked all qualify — never a remote address. Quota
 * state is PostgreSQL-persisted in {@code common.trial.persistence.device_trial_scan_event}, locked
 * and counted per device row, so it survives a restart, a redeploy, and multiple replicas; see
 * {@code docs/ARCHITECTURE.md} and {@code docs/SECURITY_BOUNDARY.md} for the full policy.
 */
package com.lyanhkhoa.linksentry.common.trial;
