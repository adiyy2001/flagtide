# 0013 Dependency license policy

Status: accepted, 2026-10-03

## Context

The brief allows only permissive dependencies (MIT, Apache 2.0, BSD, ISC) and also requires jqwik for property tests. jqwik is licensed under EPL 2.0, as is JUnit, which every Java test suite needs.

## Decision

- Everything that ends up in a published artifact, a container image or the runtime classpath is MIT, Apache 2.0, BSD or ISC. That covers Quarkus, Vert.x (used under Apache 2.0), JDBI, Flyway Community, the PostgreSQL JDBC driver, Jackson, Angular, Angular Material, RxJS, `@flagwire/*`.
- Test scope and build tooling can be under other licenses when they are not distributed with the project: JUnit and jqwik (EPL 2.0, test scope), Checkstyle (LGPL, a Maven plugin), k6 (AGPL 3.0, run from its official image, only my scripts are in the repo), Lighthouse (Apache 2.0) with its bundled axe-core (MPL 2.0), run as a tool.
- CI runs a license report for npm production dependencies (`license-checker-rseidelsohn`) and for the Maven runtime scope, and fails on anything outside the allowed list. The same reports feed `CREDITS.md`.
- Nothing is copied from other projects. MurmurHash3 is public domain and is written from the algorithm description. The reference vectors come from the SMHasher project and public test suites and are credited.

## Alternatives

- Replace jqwik with a permissively licensed property testing library: the brief names jqwik, and the alternatives are less maintained.
- Treat test dependencies like runtime ones: would rule out JUnit.

## Consequences

- `CREDITS.md` lists tools separately from dependencies and states the license of each.
- A new runtime dependency with another license fails CI until someone decides on purpose.
