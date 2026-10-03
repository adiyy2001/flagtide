# 0019 End-to-end harness and demo shop wiring

Status: accepted, 2026-10-04

## Context

The brief asks for one end-to-end claim: a change made in the admin shows up in the demo shop within one second. The admin talks to `server-a` and the shop to `server-b`, so a pass also proves that the PostgreSQL notification path works between two instances. Cypress drives one origin per test, and the admin (14200) and the shop (14300) are two origins. The demo GIF wants both on screen side by side anyway.

Three more questions came up while building it: how the shop consumes the SDK, how the stack gets its data, and which browser security headers get in the way of putting two apps into iframes.

## Decision

- A harness page, served by a 40 line Node server that Cypress starts in `setupNodeEvents` on 127.0.0.1:14400, shows the admin flag list and the shop in two iframes. The tests reach into both frames. `chromeWebSecurity` is off, so the test code can read `contentDocument` across origins. The run uses Chrome, because that switch does nothing in Firefox.
- The page has no logic of its own. The admin and the shop are the production images from `docker compose`, not dev servers, so a pass means the production build works.
- Both images allow framing by the harness only. The admin nginx template and the shop server send `Content-Security-Policy: frame-ancestors 'self'` plus the harness origin, taken from `FLAGWIRE_FRAME_ANCESTORS`. Everything else stays blocked.
- Propagation is measured inside the browser. A `MutationObserver` in the shop frame stamps `Date.now()` when the banner appears or disappears, and the test stamps the click the same way, so the number excludes Cypress command polling. A timing taken around Cypress commands read 590 ms for the same change that took 30 ms.
- Tests that need a prediction (the rollout test) call `@flagwire/core` from a Cypress task, built from `dist/libs/core`. The test picks a visitor key by searching for one whose bucket sits near the middle of the range, so a small weight change moves it across the boundary. The `e2e` target depends on the core build.
- The offline test stops `server-a` and `server-b` with `docker compose stop` through a task, reloads the shop and expects the banner from the stored snapshot. It restarts them in `after`.
- The demo shop imports the SDK through the TypeScript path mappings, like the admin does with `@flagwire/core`, and not from `dist`. The packed packages are already exercised by `angular:pack-check`, and this keeps the shop build one step shorter.
- The stack is seeded by a one-shot compose service (`docker/seed`), a plain Node script that talks to the REST API with the dev admin keys. It creates what is missing and leaves existing flags alone, so a second `up` changes nothing. `docker compose up --wait` accepts it because the dependants wait for `service_completed_successfully`.

## Alternatives

- Two Cypress tests that never share a page: toggle through the admin REST API and assert in the shop. It is the fallback in the plan. It misses the admin UI entirely, so the click in the admin and the one second claim are not tied together.
- `cy.origin` with a second visit in the same test: it works for sequential steps, and it cannot watch the shop change while the admin is clicked.
- Playwright instead of Cypress: it handles two pages well, and the brief asks for Cypress.
- A SQL file or Flyway seed: it would put demo flags into the production migration path.
- Running the shop with `ng serve` in the E2E run: faster to start, and it would not test the SSR server, the headers or the Docker image.

## Consequences

- The suite needs the whole compose stack, Chrome and about a minute of wall time. It is not part of `pnpm verify`. `pnpm nx run e2e:e2e` runs it, and the node tests for the seed and the harness run in `e2e:test` and stay inside `verify`.
- `chromeWebSecurity: false` is a test-only setting. The images keep their headers.
- Cypress reloads the spec when a test first visits another origin, so a `before` hook can run twice. State that must survive goes through the `remember` task, and `before` hooks that only start containers are safe to repeat.
- The offline test stops the two servers for about twenty seconds and cannot run next to anything else that uses the stack.
- Writing this suite found a real defect: a snapshot handed over by the server render was never written to storage, so the first visit after a rendered page left nothing for an offline start. The client now persists the initial snapshot.
