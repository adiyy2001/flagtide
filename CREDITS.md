# Credits

flagwire is written from scratch. This file lists what it builds on. `pnpm check:licenses` reads the runtime closure of the shipped code and fails on any license outside the policy in [ADR 0013](docs/adr/0013-dependency-license-policy.md). It writes the full lists to [`docs/licenses`](docs/licenses): 33 npm packages and 162 Maven artifacts at the time of writing.

## Runtime dependencies, server

| Library | License | Used for |
| --- | --- | --- |
| [Quarkus](https://github.com/quarkusio/quarkus) 3.40.1 | Apache-2.0 | Application framework, REST, WebSockets Next, OpenAPI, health, CDI |
| [Eclipse Vert.x](https://github.com/eclipse-vertx/vert.x) 4.5.34 | EPL-2.0 or Apache-2.0 | The reactive PostgreSQL client behind `LISTEN` |
| [JDBI](https://github.com/jdbi/jdbi) 3.55.0 | Apache-2.0 | Named-parameter SQL |
| [Flyway](https://github.com/flyway/flyway) 12.0.0 | Apache-2.0 | Schema migrations |
| [Agroal](https://github.com/agroal/agroal) 3.2.1 | Apache-2.0 | Connection pool |
| [PostgreSQL JDBC driver](https://github.com/pgjdbc/pgjdbc) 42.7.13 | BSD-2-Clause | PostgreSQL access |
| [Jackson](https://github.com/FasterXML/jackson) 2.21.7 | Apache-2.0 | JSON in the adapters (the domain has its own small codec) |
| [SmallRye](https://github.com/smallrye) libraries | Apache-2.0 | OpenAPI, config, health, reactive messaging primitives |
| [Netty](https://github.com/netty/netty) 4.1.138 | Apache-2.0 | Transport under Vert.x |
| [SLF4J](https://github.com/qos-ch/slf4j) 2.0.18 | MIT | Logging facade |

Eight Jakarta EE API jars (annotation, interceptor, resource, transaction, JSON, WS-RS, EL and similar) are EPL-2.0 with the GPL-2.0 Classpath Exception. They are API jars that Quarkus itself ships. The exception is named in [ADR 0013](docs/adr/0013-dependency-license-policy.md).

## Runtime dependencies, web

| Package | License | Used for |
| --- | --- | --- |
| [Angular](https://github.com/angular/angular) 22.2.1 | MIT | The SDK, the admin and the demo shop |
| [Angular Material and CDK](https://github.com/angular/components) 22.2.1 | MIT | Admin components, drag and drop |
| [RxJS](https://github.com/ReactiveX/rxjs) 7.8.2 | Apache-2.0 | The connection manager |
| [tslib](https://github.com/microsoft/tslib) 2.8.1 | 0BSD | Required by compiled TypeScript |

## Development and test tools

| Tool | License | Used for |
| --- | --- | --- |
| [Nx](https://github.com/nrwl/nx) 23.2.1 | MIT | Task graph and cache |
| [pnpm](https://github.com/pnpm/pnpm) 12.8.1 | MIT | Package manager |
| [TypeScript](https://github.com/microsoft/TypeScript) 6.0.3 | Apache-2.0 | Type checking and compilation |
| [Vitest](https://github.com/vitest-dev/vitest) 5.0.3 | MIT | Unit tests for the web code |
| [fast-check](https://github.com/dubzzz/fast-check) 4.10.2 | MIT | Property tests in TypeScript |
| [semver](https://github.com/npm/node-semver) 7.8.5 | ISC | Differential test of the TypeScript semver code only, nothing shipped |
| [ws](https://github.com/websockets/ws) 8.22.0 | MIT | Fake WebSocket server in the SDK tests |
| [jsdom](https://github.com/jsdom/jsdom) 30.1.1 | MIT | DOM in component tests |
| [Cypress](https://github.com/cypress-io/cypress) 16.1.1 | MIT | End-to-end tests |
| [Playwright](https://github.com/microsoft/playwright) 1.63.0 | Apache-2.0 | Screenshots, browser benchmark and the demo recording |
| [tinybench](https://github.com/tinylibs/tinybench) 6.2.0 | MIT | SDK evaluation benchmark |
| [openapi-typescript](https://github.com/openapi-ts/openapi-typescript) 7.13.0 | MIT | Admin API types generated from the OpenAPI file |
| [ng-packagr](https://github.com/ng-packagr/ng-packagr) 22.2.4 | MIT | Building the Angular SDK |
| [ESLint](https://github.com/eslint/eslint) 10.12.0, [typescript-eslint](https://github.com/typescript-eslint/typescript-eslint) 8.71.0, [angular-eslint](https://github.com/angular-eslint/angular-eslint) 22.5.0 | MIT | Linting |
| [Prettier](https://github.com/prettier/prettier) 3.9.9 | MIT | Formatting |
| [tsx](https://github.com/privatenumber/tsx) 4.23.15 | MIT | Running TypeScript scripts |
| [JUnit](https://github.com/junit-team/junit-framework) 6.1.3 | EPL-2.0 | Java tests, test scope |
| [jqwik](https://github.com/jqwik-team/jqwik) 1.10.1 | EPL-2.0 | Property tests in Java, test scope |
| [AssertJ](https://github.com/assertj/assertj) 3.27.7 | Apache-2.0 | Assertions |
| [ArchUnit](https://github.com/TNG/ArchUnit) 1.5.1 | Apache-2.0 | Architecture rules |
| [Testcontainers](https://github.com/testcontainers/testcontainers-java) 2.0.5 | MIT | PostgreSQL in integration tests |
| [REST Assured](https://github.com/rest-assured/rest-assured) 6.0.1 | Apache-2.0 | REST tests |
| [Awaitility](https://github.com/awaitility/awaitility) 4.3.0 | Apache-2.0 | Waiting on asynchronous results in tests |
| [JaCoCo](https://github.com/jacoco/jacoco) 0.8.15 | EPL-2.0 | Java coverage |
| [Checkstyle](https://github.com/checkstyle/checkstyle) 14.3.0 | LGPL-2.1 | Style rules (`this.` prefix, no comments), build time only |
| [Spotless](https://github.com/diffplug/spotless) 3.10.3 and [google-java-format](https://github.com/google/google-java-format) 1.36.1 | Apache-2.0 | Java formatting |

JUnit, jqwik and JaCoCo are EPL-2.0 and Checkstyle is LGPL. jqwik is the property testing library I picked, and all four stay in test or build scope. Nothing from them is in a published package or in a runtime image.

## Build-time tools with other licenses

Three packages in the full npm tree are not under the permissive licenses of the runtime policy. None of them is in a published package or an image, and `scripts/license-policy.json` lists them explicitly so `pnpm check:licenses` notices if one changes.

- [lightningcss](https://github.com/parcel-bundler/lightningcss) (MPL-2.0) minifies CSS inside the Angular build.
- [caniuse-lite](https://github.com/browserslist/caniuse-lite) (CC-BY-4.0) is browser support data read by the build.
- [argparse](https://github.com/nodeca/argparse) (Python-2.0) is the argument parser inside js-yaml.

## Tools run but not installed

- [Lighthouse](https://github.com/GoogleChrome/lighthouse) 13.5.0 (Apache-2.0) runs from `scripts/lighthouse.sh`. It bundles axe-core (MPL-2.0), so it is not in `package.json`.
- [k6](https://github.com/grafana/k6) 2.3.0 (AGPL-3.0) runs from its official Docker image for the load test. Its test script is mine, the program is not part of this repository.
- [actionlint](https://github.com/rhysd/actionlint) 1.7.12 (MIT) lints the workflow file from its Docker image.
- Docker images: `postgres` 18.6 (PostgreSQL license), `node` 24.21.0 (MIT), `nginxinc/nginx-unprivileged` (BSD-2-Clause), `eclipse-temurin` 21 (GPL-2.0 with the Classpath Exception). They run and are not redistributed.
- Google Chrome and Chromium drive the browser tests, the benchmark and the recording.
- ffmpeg 6 turns the Playwright video into the demo GIF. It is a command line tool on the author's machine.

## Algorithms and data

- MurmurHash3 x86 32-bit was designed by Austin Appleby (public domain). Both implementations in this repository are written from the published algorithm. Their reference vectors come from the public MurmurHash3 and SMHasher test material, including the SMHasher verification value `0xB0F57EE3`, and were cross-checked with an independent Python implementation (`spec/tools/oracle.py`).
- Semantic Versioning 2.0.0 (CC-BY-3.0 text at semver.org) defines the version comparison, including pre-release ordering.
- Percentage rollout by hashing a flag key, a salt and a context key follows a common idea in feature flag services. No code from any of them is used.

## Assets

The favicons and the shop product tiles (CSS gradients) are made for this project. The apps use the system font stack, so no font files are shipped. The demo shop is a made-up shop called Brew Bench.
