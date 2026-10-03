# 0003 Toolchain lines: stable only, and where the newest release is not usable

Status: accepted, 2026-10-03

## Context

The brief says to use the latest stable version of everything. On the day of planning, several "latest" tags are not usable together or are not stable.

## Decision

- Quarkus 3.40.1, the LTS released on 2026-09-30. Quarkus 4.0.0.Beta1 (2026-10-01) is a beta. Quarkus decides the versions of Vert.x (4.5.34), Flyway (12.0.0), Jackson, JUnit (6.1.3) and Testcontainers (2.0.5); I do not override its BOM, even where Maven Central has newer releases.
- Java 21, as the brief says. Quarkus 3.40 runs on 17 and up.
- Angular 22.2.1 with TypeScript 6.0.3. TypeScript 7.0.2 is on npm, but `@angular/compiler-cli`, `@angular/build`, ng-packagr 22 and typescript-eslint 8.71 all require `>=6.0 <6.1`.
- Cypress 16.1.1 with a plain `cypress.config.ts` and a run-commands target. `@nx/cypress` 23.2.1 only supports Cypress below 16, and the plugin adds nothing the config does not do.
- No pre-release anywhere: AssertJ stays on 3.27.7 (4.0 is a milestone), the Maven compiler plugin on 3.15.0 (4.0 is a beta), Nx on 23.2.1 (23.3 is a beta).

## Alternatives

- Quarkus 4 beta for Vert.x 5: a beta under a portfolio project that has to build from a fresh clone years from now is a bad trade.
- Cypress 15 to keep `@nx/cypress`: gives up the latest version to keep a plugin I do not need.

## Consequences

- Every pin has a reason that can be checked, and PLAN.md lists the sources.
- Moving to TypeScript 7 or Quarkus 4 later is a planned task once Angular supports the first and Quarkus ships a stable release of the second.
