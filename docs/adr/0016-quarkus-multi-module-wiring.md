# 0016 Quarkus wiring across Maven modules

Status: accepted, 2026-10-03

## Context

The use cases and adapters are plain Java in separate Maven modules. Quarkus finds CDI beans and JAX-RS resources at build time by reading a Jandex index, and by default it indexes only the application module. I ran a spike before writing the REST code.

## Decision

- `adapter-in-rest` and `adapter-out-postgres` carry the Jandex Maven plugin, so Quarkus indexes them. `domain`, `application` and `adapter-out-memory` stay free of CDI and Quarkus and are not indexed.
- `bootstrap` is the only module that knows both sides. It declares producers (`@Produces @Singleton`) for every plain use case, the clock, the id generator and the authorizer.
- The adapter is chosen at build time: `PostgresPersistence` is annotated `@IfBuildProperty(name = "flagwire.persistence", stringValue = "postgres")` and `MemoryPersistence` with `memory`. Only the selected producers exist, and a memory build does not need a datasource. The memory test profile also deactivates the datasource, Dev Services and Flyway.
- ArchUnit rules (see ADR 0005) use slices named after the layer and adapter: `adapter.in.*` may call use cases and never ports or outbound adapters, `adapter.out.*` never calls use cases and never another adapter. Packages were renamed to `dev.flagwire.adapter.in.rest`, `...out.memory` and `...out.postgres` so the patterns match.
- The enforcer plugin bans the other adapters and `bootstrap` from `adapter-in-rest`, and the build fails on any `javac` lint warning in `bootstrap` (`-Xlint:all -Werror`, with `serial` and `classfile` off because of third party class files).
- Quarkus tests run with the JaCoCo agent of surefire. See ADR 0014 for why `quarkus-jacoco` is not used.

## Alternatives

- `quarkus.index-dependency` entries in `bootstrap`: one list in one place, but a new adapter module then needs a change in another module and the index is not shipped with the library.
- CDI annotations on the use cases: fewer producers, but `application` would depend on Jakarta and the hexagonal rules would lose their point.
- Runtime selection with `@LookupIfProperty`: allows switching without a rebuild, but both adapters would be built into every image and the memory adapter would ship to production.

## Consequences

- Adding an adapter means adding the Jandex plugin, a persistence class in `bootstrap` and a value of `flagwire.persistence`.
- A forgotten Jandex plugin shows as an unsatisfied dependency at build time, not as a runtime surprise.
