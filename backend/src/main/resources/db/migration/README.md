# Flyway migrations

**Intentionally empty.** Flyway is configured and wired, but no migration exists
yet because no entity exists yet. Scan-history persistence is Exercise 10 of
[MANUAL_IMPLEMENTATION_GUIDE.md](../../../../../docs/MANUAL_IMPLEMENTATION_GUIDE.md),
and it is deliberately last: designing a table before the analyzer's output has
settled guarantees a rewrite.

Flyway ignores files that do not match its naming pattern, so this README is safe
to leave in place.

## Conventions

Name migrations `V<n>__snake_case_description.sql`, for example:

```text
V1__create_scan_history.sql
V2__add_scan_history_retention_index.sql
```

Rules:

- **Migrations are append-only.** Never edit a migration that has been applied
  anywhere — Flyway validates its checksum and will refuse to run.
- One logical change per migration.
- Explicit `NOT NULL` and explicit types. No implicit defaults.
- Verify against real PostgreSQL through Testcontainers, never H2. The `test`
  profile uses H2 for context startup only, and H2 will happily accept SQL that
  PostgreSQL rejects.

## Before writing the first migration

Decide, and record the answers in the migration's header comment:

- What is safe to store? Store the **redacted** representation. Never the raw URL
  and never the query string — query strings routinely carry tokens.
- What is the retention period, and what enforces it?
- Does each row carry the engine version that produced it? (It should, or old rows
  become uninterpretable once the rules change.)
