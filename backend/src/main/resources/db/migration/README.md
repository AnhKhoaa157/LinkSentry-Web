# Flyway migrations

The first migration creates the privacy-safe scan-history parent and ordered
finding tables. Scan-history persistence is Exercise 10 of
[MANUAL_IMPLEMENTATION_GUIDE.md](../../../../../../docs/MANUAL_IMPLEMENTATION_GUIDE.md).

Flyway ignores files that do not match its naming pattern, so this README is safe
to leave in place.

## Conventions

Name migrations `V<n>__snake_case_description.sql`, for example:

```text
V1__create_scan_history.sql
```

Rules:

- **Migrations are append-only.** Never edit a migration that has been applied
  anywhere — Flyway validates its checksum and will refuse to run.
- One logical change per migration.
- Explicit `NOT NULL` and explicit types. No implicit defaults.
- Verify against real PostgreSQL through Testcontainers, never H2. The `test`
  profile uses H2 for context startup only, and H2 will happily accept SQL that
  PostgreSQL rejects.

## Persistence decisions

- Store the **redacted** response snapshot only. Never the raw URL, credentials,
  query string, or fragment text.
- Retain rows for 30 days by default, with application cleanup driven by the
  configured retention period and injected `Clock`.
- Each row carries the engine version that produced it, so old results remain
  interpretable after rules change.
- Findings use a child table and an explicit position column, not opaque JSON.
