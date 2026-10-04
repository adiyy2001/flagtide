# 0020 The project name stays flagwire until I decide

Status: accepted, 2026-10-04

## Context

The planning step found that `flagwire` is already taken. A GitHub organization of that name exists (created 2026-08-23, with a `flagwire-js` repository of "official SDKs"), so does the npm scope `@flagwire` with packages such as `schema`, `evaluate` and `sdk-js`, and flagwire.com serves a product. A portfolio repository with the same name would confuse a reader, and the packages `@flagwire/core` and `@flagwire/angular` could not be published under that scope.

The planning step proposed `flagtide` (nothing found on npm, GitHub or in searches on 2026-10-03) and `flagrail` as a backup.

## Decision

- Folder, repository, Maven group (`dev.flagwire`), Docker names and npm scope stay `flagwire` for now. The build agents and the folder name never had to diverge, and nothing is published.
- The rename is kept mechanical. The name appears in four places only: the npm scope in the two `package.json` files and `tsconfig.base.json`, the Java package and Maven coordinates, the Docker and compose names, and the docs.
- I rename before publishing a package or announcing the repository. `NOTES_FOR_ADRIAN.md` and the final report list the steps.

## Alternatives

- Rename now: the cleanest end state, and it would have changed every identifier while eight milestones were still being built.
- Keep the name for good: free for a private repository, a problem as soon as a package is published.

## Consequences

- Until the rename, the README says `flagwire` everywhere and the package names are placeholders.
- A rename touches many files and no logic. The tests, the conformance suite and the compose stack would catch a missed spot.
