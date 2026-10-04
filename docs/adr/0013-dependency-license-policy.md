# 0013 Dependency license policy

Status: accepted, 2026-10-03

## Context

I allow only permissive dependencies (MIT, Apache 2.0, BSD, ISC) and I also want jqwik for property tests. jqwik is licensed under EPL 2.0, as is JUnit, which every Java test suite needs.

## Decision

- Everything that ends up in a published artifact, a container image or the runtime classpath is MIT, Apache 2.0, BSD or ISC. That covers Quarkus, Vert.x (used under Apache 2.0), JDBI, Flyway Community, the PostgreSQL JDBC driver, Jackson, Angular, Angular Material, RxJS, `@flagwire/*`.
- Test scope and build tooling can be under other licenses when they are not distributed with the project: JUnit and jqwik (EPL 2.0, test scope), Checkstyle (LGPL, a Maven plugin), k6 (AGPL 3.0, run from its official image, only my scripts are in the repo), Lighthouse (Apache 2.0) with its bundled axe-core (MPL 2.0), run as a tool.
- CI runs a license report for npm production dependencies (`license-checker-rseidelsohn`) and for the Maven runtime scope, and fails on anything outside the allowed list. The same reports feed `CREDITS.md`.
- Nothing is copied from other projects. MurmurHash3 is public domain and is written from the algorithm description. The reference vectors come from the SMHasher project and public test suites and are credited.

## Alternatives

- Replace jqwik with a permissively licensed property testing library: jqwik is the property testing library I want to use, and the alternatives are less maintained.
- Treat test dependencies like runtime ones: would rule out JUnit.

## Consequences

- `CREDITS.md` lists tools separately from dependencies and states the license of each.
- A new runtime dependency with another license fails CI until someone decides on purpose.

## Amendment, 2026-10-04: how the policy is enforced

`pnpm check:licenses` (`scripts/license-report.mjs`) writes `docs/licenses/npm-runtime.json` and `docs/licenses/maven-runtime.json` and exits with an error for anything outside the policy in `scripts/license-policy.json`.

- npm: the roots are the packages imported by non-test code of the two SDK packages, the admin and the demo shop, plus the dependencies and peer dependencies of the SDK manifests. The report follows required dependencies and peer dependencies from there (33 packages at the time of writing). Every package must be MIT, ISC, Apache-2.0, BSD-2-Clause, BSD-3-Clause, 0BSD, BlueOak-1.0.0, CC0-1.0 or Unlicense, and for an SPDX `OR` expression one allowed alternative is enough. Build tools such as Vite, esbuild and Lightning CSS (MPL-2.0) are not part of the closure because the Angular builder is a dev dependency.
- Maven: the `license-maven-plugin` 2.7.1 goal `aggregate-add-third-party` lists the compile and runtime scope of the `bootstrap` module. An artifact passes when one of its licenses is Apache-2.0, MIT, MIT-0, BSD, EDL 1.0 (BSD-3-Clause) or public domain. The Vert.x modules offer EPL and Apache-2.0 and are used under Apache-2.0.
- The earlier wording "everything is MIT, Apache 2.0, BSD or ISC" was too strong for the Java side. The Jakarta EE API jars that Quarkus needs (annotations, interceptors, expression language, JSON processing and its Parsson implementation, resource, transaction and JAX-RS) are dual licensed EPL-2.0 or GPL-2.0 with the Classpath Exception and have no permissive option. Eight artifacts fall in this group. They are used unmodified under EPL-2.0, which asks for nothing beyond keeping the notices when the jars are redistributed, and they are the only documented exceptions. A new exception needs an entry with a reason in the policy file and a line here.

Consequences: `CREDITS.md` states the exception openly and lists those eight artifacts under EPL-2.0.
