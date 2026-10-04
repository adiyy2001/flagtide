# 0004 Vitest instead of Jest

Status: accepted, 2026-10-03

## Context

I would use Jest unless the Angular CLI default is clearly the better fit. The Angular 22 testing guide describes Vitest as the default runner (builder `@angular/build:unit-test`), says Karma is still supported, and does not mention Jest. The Nx 23 Angular generators offer `vitest-angular`, `vitest-analog` and `jest`.

## Decision

Vitest 5.0.3 with `@vitest/coverage-v8` for every TypeScript project, including the plain `libs/core`. Angular projects use the Angular unit-test builder, which accepts Vitest `^4.0.8 || ^5.0.0`. TestBed is zoneless by default in Angular 22, so tests run the way the app runs. Marble tests use RxJS `TestScheduler`, which does not depend on the runner.

## Alternatives

- Jest 30 with `jest-preset-angular` 17: it works with Angular 22, but it is maintained outside the Angular CLI, needs a TypeScript transform layer, and I would run a second runner next to the one the CLI uses.
- Karma: real browsers, but slow and no longer the default.

## Consequences

- One runner and one coverage provider for libraries and apps, and a runner that is native ESM.
- The Jest habits carry over: `describe`, `it`, `expect` are the same. Fake timers and mocks use `vi.*` instead of `jest.*`.
