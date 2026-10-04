# 0005 Hexagonal architecture as Maven modules, checked by ArchUnit

Status: accepted, 2026-10-03

## Context

I want hexagonal architecture and DDD to show in the code and to be enforced by the build. Either modules or packages checked by ArchUnit would do.

## Decision

Maven modules under `apps/server`: `domain`, `application`, `adapter-in-rest`, `adapter-in-websocket`, `adapter-out-postgres`, `adapter-out-memory`, `bootstrap`, plus a test-only `architecture` module.

- `domain` has no dependencies in main scope and imports only the JDK.
- `application` depends on `domain`. Use cases are plain classes with constructor injection. `bootstrap` creates them with CDI producers, so the application layer has no framework annotations.
- Adapters depend on `application` and never on each other. Only `bootstrap` depends on all of them and picks the persistence adapter at build time (`flagtide.persistence=postgres|memory`).
- The Maven enforcer bans extra dependencies in `domain` and `application`. ArchUnit rules in `architecture` check imports and package cycles, written as plain tests that call `rule.check(classes)` with the core `archunit` artifact.
- Port contract tests are abstract test classes in the `application` test-jar. Both persistence adapters have to pass them.

## Alternatives

- Packages in one module: cheaper, but the compiler would not stop a stray import, only a test would.
- Putting `@ApplicationScoped` on use cases: shorter, but the application layer would import Jakarta.

## Consequences

- Library modules that hold CDI beans need a Jandex index (`quarkus.index-dependency` or the Jandex plugin). That is a known cost, handled in ADR 0016.
- The in-memory adapter is a second real implementation of every port, which is the proof that the ports are not shaped by PostgreSQL.
