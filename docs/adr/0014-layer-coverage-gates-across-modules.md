# 0014 Coverage gates per layer, measured across modules

Status: accepted, 2026-10-03

## Context

The targets are 90 percent line coverage in `domain`, 85 in `application` and 80 in the adapters. The use cases live in `application`, but their behaviour is exercised through real adapters: the use case tests and the port contract tests run in `adapter-out-memory`, because `application` cannot depend on an adapter (ADR 0005). Measured inside its own module, `application` would only show the coverage of its small unit tests.

## Decision

- `domain` and `adapter-out-memory` keep a per-module JaCoCo check (90 and 80 percent).
- `application` skips the per-module check. The `architecture` module merges the `jacoco.exec` files of `domain`, `application` and `adapter-out-memory`, unpacks their classes into its own output directory and runs three `check` executions with class filters: `dev/flagwire/domain/**` at 90, `dev/flagwire/application/**` at 85 and `dev/flagwire/adapter/**` at 80. A merged HTML report is written to `architecture/target/site/jacoco-merged`.
- Every adapter module added later joins the merge list and the adapter gate.
- The port contract tests and their helpers (`TestAdapters`, `Samples`, `MutableTimeSource`, `SequentialIds`) are published as the `application` test-jar. An adapter module supplies one `TestAdapters` implementation and one subclass per contract.

## Alternatives

- Duplicating the use case tests in `application` against a hand written fake: it would test the fake and double the maintenance.
- `jacoco:report-aggregate` without a check: it only reports, so nothing would fail.
- Moving the use case tests into `application` with the in-memory adapter as a test dependency: it breaks the rule that `application` depends only on `domain` (the enforcer would have to make an exception).

## Consequences

- The aggregate gates run in the `package` and `verify` phases of the last module, so a plain `mvn test` does not evaluate them.
- Unpacked classes are byte for byte the ones the tests ran against, so JaCoCo matches them by class id. The architecture tests ignore the unpacked copies in `architecture/target/classes`.
- The in-memory adapter serializes all writers with one lock instead of one lock per environment. A flag edit touches every environment of a project in one transaction, so a per-environment lock would not keep the invariants. Per-environment ordering of versions still holds because the change log assigns them inside that lock.

## Amendment

- The merge now reads `domain`, `application`, `adapter-out-memory`, `adapter-out-postgres` and `bootstrap`. The REST adapter has no tests of its own: `@QuarkusTest` flows in `bootstrap` drive it, and the PostgreSQL adapter is covered by its contract tests, so both are measured through the merged data. `adapter-in-rest`, `adapter-out-postgres` and `bootstrap` skip the per-module check.
- Six artifacts are unpacked into `architecture/target/classes` and `dev/flagwire/adapter/**` stays at 80 (the packages are `adapter.in.rest`, `adapter.out.memory` and `adapter.out.postgres`). A fourth execution gates `dev/flagwire/bootstrap/**` at 80.
- The `quarkus-jacoco` extension was tried first and dropped. It instruments only Jandex indexed application archives, writes a separate `jacoco-quarkus.exec` that was not produced reliably for library modules, and duplicates what the plain agent in surefire already records for the Quarkus test JVM. The plain agent file `target/jacoco.exec` of each module is the one merged.
