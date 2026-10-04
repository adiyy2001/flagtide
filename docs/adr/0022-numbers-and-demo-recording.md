# 0022 Where the README numbers and the demo GIF come from

Status: accepted, 2026-10-04

## Context

Every number in the README comes from a script in the repo, and no number is estimated and presented as measured. The demo GIF is recorded with Playwright and converted with ffmpeg, under 8 MB.

## Decision

- Each measurement writes a JSON file to `bench/results` that carries the hardware it ran on: `propagation-k6.json` (`pnpm bench:load`), `sdk-eval-node.json` and `sdk-eval-chromium.json` (`pnpm nx run core:bench`), `lighthouse.json` (`scripts/lighthouse.sh`), `coverage.json` (`pnpm coverage:report`) and `test-counts.json` (`pnpm test:counts`, which runs the web and server tests and reads their summaries).
- `pnpm report` reads those files and prints the tables that the README copies, and it writes the coverage badge `docs/media/coverage.svg`. The badge shows the lowest line coverage of the eight measured areas, because a single blended number would hide a weak spot.
- The README copies numbers from `pnpm report` and never rounds them up. Where runs differ, the README says so: five propagation runs on one machine gave a p95 between 89 and 169 ms.
- The demo GIF is made by `scripts/record-demo.mjs`. It starts the harness page from the end-to-end suite (admin and shop in two iframes), drives them with Playwright against the compose stack, records a video, and converts it with ffmpeg in two passes (palette, then dither) to 960 pixels wide at 10 frames per second. The script fails when the file is 8 MB or larger.
- The visitor in the recording is chosen with `@flagtide/core` so that its bucket sits near the middle of the rollout. A small weight change then moves it across the boundary, which makes the effect visible on screen.

## Alternatives

- Hand-copied numbers in the README: the usual way a README drifts from the code.
- A screen recorder: not repeatable, and it would leave the GIF without a script behind it.
- Higher frame rate and resolution: the GIF would pass 8 MB for little gain, since the interesting events are a few state changes.

## Consequences

- Rerunning the scripts on another machine changes the numbers and the hardware line together.
- The GIF can be regenerated after any UI change with one command and a running stack.
