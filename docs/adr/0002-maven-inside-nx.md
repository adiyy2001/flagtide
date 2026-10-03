# 0002 Maven inside Nx through run-commands targets

Status: accepted, 2026-10-03

## Context

The brief asks me to either use `@nxrocks/nx-quarkus` or wrap Maven in run-commands targets. The Quarkus server is a multi-module Maven reactor, and Maven stays the source of truth for the Java dependency graph.

## Decision

One Nx project, `server`, defined in `apps/server/project.json`. Its targets (`build`, `test`, `lint`, `verify`, `serve`, `clean`) are `nx:run-commands` calls to the Maven Wrapper: `apps/server/mvnw -B -ntp -f apps/server/pom.xml <goals>`. Cache inputs are `apps/server/**/*` and `spec/vectors/**/*`, outputs are the `target` directories. The wrapper pins Maven 3.9.16, so a developer and CI run the same Maven regardless of what is installed.

## Alternatives

- `@nxrocks/nx-quarkus` 9.0.1: last release in March 2025, it mostly generates a project and wraps the same Maven goals. A stale plugin for a thin job is a liability.
- `@nx/maven` 23.2.1: official, infers one Nx project per module from the POMs, and its own README calls it experimental. Worth a look again when that note is gone.
- No integration at all, two separate build systems: loses the single `pnpm verify` and the shared CI graph.

## Consequences

- Nx sees the whole Java side as one project. There is no per-module affected analysis, which does not matter at this size. Maven's own module ordering and incremental compilation still apply inside.
- Targets are easy to read and to debug by running the same Maven command by hand.
