# 0007 Persistence with JDBC, JDBI, Flyway and JSONB documents

Status: accepted, 2026-10-03

## Context

The persistence adapter needs migrations, named query parameters, transactions that also carry the change log and the notification, and an in-memory twin that behaves the same. The brief wants the Vert.x reactive client for LISTEN/NOTIFY. It does not say what to use for the rest.

## Decision

- The application ports are blocking. The PostgreSQL adapter uses Agroal (Quarkus datasource), Flyway on the same datasource, and JDBI 3.55.0 for queries with named parameters. Transactions go through a `TransactionRunner` port implemented with `QuarkusTransaction`, so use cases do not import Jakarta.
- Aggregates and change log entries are stored as JSONB documents next to a few indexed columns (ids, keys, revision, version, timestamps). Two codecs in `domain` (`JsonText` and the document classes next to the aggregates) write and read those documents, so neither the domain nor the adapter carries Jackson mixins and the domain stays free of annotations. The adapter only passes the text through `jsonb` parameters.
- Rows are addressed by natural keys (project key, flag key, environment key, segment key) and the surrogate id stays a plain column. The environment version lives in its own `environment_versions` row, which the change log append bumps with `UPDATE ... RETURNING` in the writing transaction.
- Time columns are `timestamptz` and map to `ZonedDateTime` in UTC.
- The reactive client (`PgSubscriber`, Vert.x 4.5 from the Quarkus BOM) is used only for LISTEN. Quarkus supports a JDBC and a reactive datasource side by side. `pg_notify` is called through JDBC inside the writing transaction.
- Blocking REST methods run on Quarkus worker threads. The WebSocket hot path does not touch the database (see ADR 0008).

## Alternatives

- Hibernate ORM with Panache: standard, but mapping a rule tree with polymorphic serve types onto tables adds a lot of mapping for no gain, and the entities would leak into the domain or duplicate it.
- Vert.x SQL client with templates for everything: named parameters and non-blocking, but every port would return `Uni` and the in-memory adapter and the domain tests would pay for it.
- jOOQ: typed SQL, but it needs a live database for code generation in the build.

## Consequences

- A second JDBC adapter (the Oracle stretch goal) would reuse most of the code.
- JSONB makes schema evolution of the aggregate a Jackson concern. Flyway migrations still own the relational parts, and a version field inside each document leaves room for upcasting.

## Amendment, M3

The first draft of this record proposed Jackson mixins. The implementation uses the `JsonText` codec instead because it removed the mixin classes without losing the annotation free domain, and it let the in-memory adapter and the REST adapter share the exact wire format of compiled flag configs.
