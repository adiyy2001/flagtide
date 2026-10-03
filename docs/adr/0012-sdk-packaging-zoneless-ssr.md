# 0012 SDK packaging, zoneless and SSR

Status: accepted, 2026-10-03

## Context

The brief wants a publishable Angular SDK that works zoneless and under SSR, keeps the last snapshot for an offline start, and includes a dev overrides panel. It also wants a framework-agnostic core with the evaluation engine and the RxJS connection manager.

## Decision

- Two packages: `@flagwire/core` (no Angular, depends on RxJS as a peer dependency) and `@flagwire/angular` (depends on `@flagwire/core`, peers on Angular 22). Both are ESM only with `sideEffects: false`. The scope is a placeholder until the project name is settled.
- `@flagwire/angular` is built with ng-packagr through `@nx/angular:package`, and `npm pack` is part of CI. Publishing is not done by the build.
- Zoneless only. The SDK uses signals and does not need zone.js, and the tests run with the zoneless TestBed that Angular 22 gives by default.
- SSR: on the server platform the SDK opens no socket and touches neither `window` nor `localStorage`. It fetches the snapshot with `fetch` using a server-side URL, evaluates flags during rendering, and passes the snapshot to the browser through `TransferState`, so the first client render matches the server render. Then the browser connects with the version from that snapshot.
- Offline start: the last snapshot is written to storage (wrapped in try/catch, because storage can throw) and used while the status is `stale`.
- The dev overrides panel is a standalone OnPush component in the secondary entry point `@flagwire/angular/overrides`, so applications that do not import it do not ship it. Overrides win over server values and are reported with reason `OVERRIDE`.
- Public API: `provideFlagwire(config)`, `injectFlag<T>(key, fallback): Signal<T>`, the structural directive `*flagwireFlag`, `flagwireGuard(key, options)` returning a `CanMatchFn`, and a status signal. Only these carry TSDoc.

## Alternatives

- One package with the engine inside the Angular library: simpler to publish, but the engine could not be used in React or Node and the conformance suite would test an Angular package.
- Bundling the core into the Angular package: avoids a second package, hides the dependency and duplicates code when both are installed.
- Connecting on the server too: no use, a render is short lived and the server has no one to push to.

## Consequences

- Two versions to keep in step, handled by the workspace.
- Server and browser use different URLs for the same service in the demo shop (container network address versus published port), so the config takes both.

## Amendment (milestone 5)

- ng-packagr runs directly from an Nx run-commands target (`ng-packagr -p libs/angular/ng-package.json`) instead of `@nx/angular:package`. The plugin would add a second Angular toolchain on top of the Angular CLI that already runs the unit tests, and the direct call produces the same partial-compilation output. Nx still caches the build and orders it after `core:build`.
- The Angular library carries its own `angular.json` so that `ng test angular` runs the specs with the Angular vitest runner and jsdom. Server rendering is tested separately in a Node environment (`test-ssr`) so that no DOM globals exist at all.
- The SDK checks for browser globals with `in globalThis` and resolves `WebSocket` when the first socket opens, which is why the server test can trap every access to `window`, `document`, `localStorage`, `sessionStorage` and `WebSocket`.
- The wire frame uses `v` for the version, while the in-memory and transferred snapshot uses `version`.
