# 0021 What is not built, and why

Status: accepted, 2026-10-04

## Context

Four stretch goals were on the list: flag prerequisites with cycle detection, scheduled changes, a second persistence adapter for Oracle, and an SSE fallback. Stretch goals go before tests or correctness do. The must-haves took all of the time, including a load test with 5,000 sockets, a two-language conformance suite and a production-image end-to-end run.

## Decision

None of the four stretch goals is built. Every must-have is, and each one is checked against the production images by `scripts/verify-must-haves.sh`.

- Prerequisites need a spec change (an evaluation order for flags that depend on other flags), a cycle check in the aggregate and vectors in both languages. It would be one more thing that can make Java and TypeScript disagree, and the spec has a `specVersion` field for exactly this kind of addition.
- Scheduled changes need a scheduler that behaves on two instances (one runner, no double fire). That is a design of its own.
- The Oracle adapter is the one I would build first. The ports already have contract tests that the in-memory and PostgreSQL adapters both pass, so a third adapter has a clear target. It needs the `gvenzl/oracle-free` container in CI, which is heavy for a shared laptop.
- SSE would add a second transport next to WebSockets and double the reconnect and status tests. The server speaks WebSockets, and the acknowledgements that feed the propagation monitor travel back on the same socket.

## Alternatives

- Build prerequisites in a reduced form (no cycle detection): contradicts what the feature is supposed to do.
- Skip the load test to make room: the propagation target is one of the two claims of the project.

## Consequences

- The README lists these under limitations and next steps.
- The in-memory adapter is the evidence that the persistence ports are real, because the Oracle adapter does not exist.
