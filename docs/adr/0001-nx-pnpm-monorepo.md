# 0001 Nx 23 monorepo with pnpm 12 on Node 24

Status: accepted, 2026-10-03

## Context

The repo holds a Java back end, two npm packages, two Angular apps, an E2E project and benchmarks. I want one checkout, one install, one command that verifies everything, and CI that only rebuilds what a change touches.

## Decision

- Nx 23.2.1 with the plugins `@nx/js`, `@nx/angular`, `@nx/eslint` and `@nx/vitest`, all on the same version.
- pnpm 12.8.1, pinned in `packageManager`. Node 24 (`engines.node` is `^24.15.0` because Angular 22 requires that minimum).
- Fixed Nx project names: `core`, `angular`, `admin`, `demo-shop`, `e2e`, `spec`, `server`. The libraries keep their public package names `@flagtide/core` and `@flagtide/angular` through `tsconfig.base.json` paths.
- The vectors in `spec/` are a named input of every conformance and test target that reads them, so a changed vector invalidates the cache of both languages.

## Alternatives

- npm workspaces: no task graph, no cache, no affected commands.
- Turborepo: a good task runner, but no Angular generators and no ng-packagr integration.
- Angular CLI workspace alone: fine for the front end, no story for the Java side.

## Consequences

- One lockfile and a task cache shared between local runs and CI.
- The pinned Node minimum rules out the older Node 24 releases that are still the default on some machines. The scripts and CI export the right version explicitly.
