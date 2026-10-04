# 0006 Aggregate boundaries, concurrency and the API key model

Status: accepted, 2026-10-03

## Context

A flag has variants that every environment refers to, and rules and rollouts that differ per environment. Invariants such as "every served variant exists" and "weights sum to exactly 100 percent" cross that line. The service also needs optimistic concurrency (409), a per-environment version that only grows, and API keys per environment.

## Decision

- `Flag` is one aggregate. It holds the definition (key, type, variants) and one `FlagEnvironmentConfig` per environment. Removing or retyping a variant is checked against every environment in the same consistency boundary, so no cross-aggregate rule is needed.
- `Segment` is a separate aggregate scoped to an environment. A rule refers to a segment by key. The application layer passes the environment's segment keys into the command, the aggregate checks the reference. Deleting a segment that a flag uses is rejected by the use case.
- Optimistic concurrency uses a `revision` on the flag (and on the segment). `If-Match` carries it, a mismatch gives `409`. The environment version is a different thing: it is a counter per environment, assigned by the change log append in the same transaction, and used by clients to ask for deltas.
- One edit of a flag in three environments is possible in one command and produces one change log entry, so one version, per environment touched.
- API keys belong to one environment. An admin key can read the whole project, write the configuration and segments of its own environment, and manage project-wide flag definitions (create, variants, archive). An SDK key can only read snapshots and open the stream. Admin keys are stored as SHA-256 hashes and shown once. SDK keys are stored in plain text because they ship to browsers by design and the admin UI shows them. The audit author is the key label.

## Alternatives

- `Flag` and `FlagEnvironmentConfig` as two aggregates: smaller write conflicts, but the variant invariants would turn into eventual consistency.
- One revision per environment config: more precise conflicts, but a definition edit would need to touch all revisions.

## Consequences

- Two editors changing different environments of the same flag at the same time conflict. At this scale that is acceptable, and the conflict dialog in the admin shows the other change.
- Flags stay small enough (a handful of variants, tens of rules) that loading the whole aggregate is cheap.
