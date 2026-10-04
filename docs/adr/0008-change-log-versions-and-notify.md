# 0008 Change log, per-environment versions and LISTEN/NOTIFY

Status: accepted, 2026-10-03

## Context

Clients ask for "everything since version N". Several server instances must learn about each other's writes without a broker. PostgreSQL `NOTIFY` is delivered at commit and in commit order, but the payload is limited to 8,000 bytes and a notification is lost if nobody is listening at that moment.

## Decision

- Each environment has a `version`. Appending to the change log runs `UPDATE environments SET version = version + 1 ... RETURNING version` and inserts `(environment_id, version, committed_at, changes)` in the same transaction as the flag write, the audit row and `pg_notify`. The row lock serializes writers per environment, so versions have no gaps and follow commit order.
- `changes` holds the already compiled client format of every changed flag or segment (upsert or remove). Replaying a delta needs no recomputation.
- The notification payload is a pointer: environment id and version. A listener reads the entries after the version it has cached.
- Every instance listens, including the one that wrote, so there is one path for fan-out.
- After any LISTEN reconnect, an instance reads the current version of every environment it serves and the change log after its cached version. That repairs lost notifications.
- Retention keeps the last 1,000 entries per environment (configurable). A client that is older than the oldest kept entry, or ahead of the server (for example after a database reset), gets a snapshot.
- Each instance keeps a bounded ring of recent entries per environment and a serialized snapshot per `(environment, version)` in memory.

## Alternatives

- Full change in the payload: breaks on the 8,000 byte limit.
- Polling the change log: simple, but it adds latency up to the polling interval and constant database load.
- An external broker (Redis, NATS, Kafka): works, but I wanted no extra moving part and PostgreSQL already holds the truth.
- `LISTEN` through the JDBC driver: possible with polling, and the Vert.x reactive client does it without polling.

## Consequences

- The writer pays one extra round trip through the database before its own clients see the change. In compose that is a few milliseconds.
- The two-instance integration test has to kill the listener's backend and prove that the resync works.
