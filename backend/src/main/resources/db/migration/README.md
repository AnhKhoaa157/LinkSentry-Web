# Flyway migrations

| Version | Change |
| --- | --- |
| V1 | Privacy-safe scan-history parent and ordered finding tables |
| V2, V3 | Email/password accounts and registration codes (retired) |
| V4 | Device installations and administrator-granted licenses; scans owned by a license |
| V5 | Administrator login and sessions |
| V6 | Device-scoped trial scan events (M6) |
| V7 | Drops the retired V2/V3 account tables and `scan_history.owner_user_id` |

V2 and V3 still run on a new database, because migrations are append-only, and
V7 then removes what they created.

Flyway ignores files that do not match its naming pattern, so this README is safe
to leave in place.

## Conventions

Name migrations `V<n>__snake_case_description.sql`, for example:

```text
V1__create_scan_history.sql
V2__create_accounts_and_assign_scan_owners.sql
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
- Legacy rows with a null owner are preserved but never returned by authenticated
  owner-filtered retrieval.
- Findings use a child table and an explicit position column, not opaque JSON.
