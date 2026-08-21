-- Persistent, device-scoped anonymous trial quota (ADR 0010). Replaces the
-- in-memory, IP-keyed trial store: an admitted trial scan for a non-licensed
-- device is now one row here, counted under a row lock on the parent device
-- instead of a JVM-heap deque. Only an event id, the device id, and an
-- admission timestamp are ever stored — no raw URL, credential, remote
-- address, query, fragment, or user-agent.
CREATE TABLE device_trial_scan_event (
    event_id     UUID PRIMARY KEY,
    device_id    UUID NOT NULL REFERENCES device_installation (device_id) ON DELETE CASCADE,
    admitted_at  TIMESTAMPTZ NOT NULL
);

-- Serves the per-device lock-scoped prune/count/insert transaction
-- (common.trial.persistence.DeviceTrialQuotaService).
CREATE INDEX device_trial_scan_event_device_admitted_idx
    ON device_trial_scan_event (device_id, admitted_at);

-- Serves the batched stale-event sweep, which filters across all devices by
-- admitted_at alone; the composite index above cannot serve that query
-- efficiently because device_id is its leading column.
CREATE INDEX device_trial_scan_event_admitted_idx
    ON device_trial_scan_event (admitted_at);
