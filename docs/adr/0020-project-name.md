# 0020 The project is called flagtide

Status: accepted, 2026-10-04

## Context

I started the project as `flagwire`. That name is already taken: a GitHub organization of that name exists (created 2026-08-23, with a `flagwire-js` repository of "official SDKs"), so does the npm scope `@flagwire` with packages such as `schema`, `evaluate` and `sdk-js`, and flagwire.com serves a product. A portfolio repository with the same name would confuse a reader, and `@flagwire/core` and `@flagwire/angular` could not be published under that scope.

## Decision

The project is `flagtide`. On 2026-10-04 I found no npm package, no npm scope, no GitHub user or organization and no GitHub repository with that name. The npm scope is `@flagtide`, the Java group and base package are `dev.flagtide`, and the Docker and compose names follow.

The rename touched identifiers only. The name is exactly as long as the old one, so no hash input, key prefix or line width changed. The conformance suite, the unit tests and the compose stack ran again after the rename.

## Alternatives

- Keep `flagwire` and change only the npm scope: the repository would still share its name with an existing product.
- `flagrail`: also free when I checked, but `flagtide` describes the behavior better: a change moves out to every connected client at once.

## Consequences

- The `@flagtide` scope has to be created on npm before the first publish. Nobody owns it yet.
- The API key prefixes `fwa_` and `fws_` stay as they are. They are identifiers in stored data and in the seed, not the name of the project.
