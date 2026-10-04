# Configuration

Every setting that `compose.yaml`, the images and the scripts read. Defaults are the values the compose stack uses. All published ports bind to `127.0.0.1`.

## Compose level

| Variable | Default | Meaning |
| --- | --- | --- |
| `FLAGWIRE_PORT_POSTGRES` | `15432` | Host port of PostgreSQL |
| `FLAGWIRE_PORT_SERVER_A` | `18081` | Host port of the first server instance (REST, WebSocket, OpenAPI) |
| `FLAGWIRE_PORT_SERVER_B` | `18082` | Host port of the second server instance |
| `FLAGWIRE_PORT_ADMIN` | `14200` | Host port of the admin app |
| `FLAGWIRE_PORT_DEMO_SHOP` | `14300` | Host port of the demo shop |
| `FLAGWIRE_CORS_ORIGINS` | the four loopback origins of the admin and the shop | Comma separated origins that may call the REST API from a browser and open the stream |
| `FLAGWIRE_UID`, `FLAGWIRE_GID` | `1000` | User and group of the `k6` container, so results land owned by you. `bench/run-load.sh` sets them from `id` |
| `FLAGWIRE_LOAD_VUS` | `50` | k6 virtual users |
| `FLAGWIRE_LOAD_SOCKETS_PER_VU` | `100` | WebSockets per virtual user (50 times 100 gives 5,000 clients) |
| `FLAGWIRE_LOAD_ROUNDS` | `30` | Number of flag changes, one per second |

## Server (`server-a`, `server-b`)

| Variable | Default | Meaning |
| --- | --- | --- |
| `FLAGWIRE_INSTANCE_ID` | empty, then a random id | Name of the instance in logs and in the propagation statistics |
| `QUARKUS_DATASOURCE_JDBC_URL` | none, set by compose | JDBC URL of PostgreSQL |
| `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD` | `flagwire` in compose | Database credentials |
| `JAVA_TOOL_OPTIONS` | `-Xmx512m -XX:+ExitOnOutOfMemoryError` | JVM options |
| `QUARKUS_HTTP_HOST`, `QUARKUS_HTTP_PORT` | `0.0.0.0` in the image, `127.0.0.1` outside it, `8080` | Listen address inside the container. Compose publishes it on loopback only |
| `FLAGWIRE_SEED_ENABLED` | `true` on `server-a`, `false` on `server-b` | Create the demo project and its keys at start when they do not exist |
| `FLAGWIRE_SEED_PROJECT`, `FLAGWIRE_SEED_NAME` | `demo`, `Demo` | Key and name of the seeded project |
| `FLAGWIRE_SEED_KEYS_<ENV>_ADMIN`, `FLAGWIRE_SEED_KEYS_<ENV>_SDK` | demo keys in compose | Key values for `DEV`, `STAGING` and `PROD`. Admin keys start with `fwa_`, SDK keys with `fws_`. Demo values only, never reuse them |

Properties without an environment variable in compose can be set the same way (`flagwire.propagation.ring-capacity` becomes `FLAGWIRE_PROPAGATION_RING_CAPACITY`):

| Property | Default | Meaning |
| --- | --- | --- |
| `flagwire.persistence` | `postgres` | Build time choice of the persistence adapter, `postgres` or `memory` |
| `flagwire.change-log.retention` | `1000` | Change log entries kept per environment |
| `flagwire.propagation.ring-capacity` | `1000` | Entries held in memory per environment to answer reconnects with deltas |
| `flagwire.propagation.heartbeat-interval` | `15S` | Time between heartbeat frames |
| `flagwire.propagation.key-cache-ttl` | `30S` | How long an authenticated key is cached |
| `flagwire.stream.allowed-origins` | same as `FLAGWIRE_CORS_ORIGINS` | Origins allowed to open a socket. A request without an `Origin` header is allowed |

## Admin (`admin`)

| Variable | Default | Meaning |
| --- | --- | --- |
| `FLAGWIRE_ADMIN_API_URL` | `http://127.0.0.1:18081` | Base URL of the API as the browser sees it |
| `FLAGWIRE_ADMIN_PROJECT` | `demo` | Project the admin edits |
| `FLAGWIRE_ADMIN_KEYS` | the three demo admin keys as JSON | Admin key per environment. Written to `config.json` at container start, so anyone who can open the admin can read them. Fine for a local demo, not for a shared deployment |
| `FLAGWIRE_FRAME_ANCESTORS` | `'self'` and the e2e harness origin `127.0.0.1:14400` | Value of the CSP `frame-ancestors` directive |

## Demo shop (`demo-shop`)

| Variable | Default | Meaning |
| --- | --- | --- |
| `FLAGWIRE_SHOP_SDK_KEY` | the dev SDK key of the demo project | Key the shop uses in the browser and on the server |
| `FLAGWIRE_SHOP_STREAM_URL` | `ws://127.0.0.1:18082/sdk/v1/stream` | WebSocket URL the browser connects to |
| `FLAGWIRE_SHOP_SNAPSHOT_URL` | `http://127.0.0.1:18082` | API base URL the server render fetches its snapshot from (inside compose: `http://server-b:8080`) |
| `FLAGWIRE_SHOP_FRAME_ANCESTORS` | `'self'` and the e2e harness origin | Value of the CSP `frame-ancestors` directive |
| `HOST`, `PORT` | `0.0.0.0` in the image, `8080` | Listen address inside the container |

## Seed job (`seed`)

| Variable | Default | Meaning |
| --- | --- | --- |
| `FLAGWIRE_SEED_API` | `http://server-a:8080` | API the flags and segments are created through |
| `FLAGWIRE_SEED_PROJECT` | `demo` | Project name |
| `FLAGWIRE_SEED_ADMIN_KEYS` | the three demo admin keys as JSON | Keys used for the writes |

## Scripts and tests

| Variable | Used by | Meaning |
| --- | --- | --- |
| `FLAGWIRE_IT_URL`, `FLAGWIRE_IT_PROJECT`, `FLAGWIRE_IT_ADMIN_KEY`, `FLAGWIRE_IT_SDK_KEY` | `pnpm nx run core:integration` | Stack the SDK integration tests run against |
| `FLAGWIRE_E2E_ADMIN_URL`, `FLAGWIRE_E2E_SHOP_URL`, `FLAGWIRE_E2E_API_A`, `FLAGWIRE_E2E_API_B` | Cypress | Where the end-to-end suite finds the stack |
| `FLAGWIRE_SMOKE_PROJECT`, `FLAGWIRE_SMOKE_DEV_ADMIN_KEY`, `FLAGWIRE_SMOKE_DEV_SDK_KEY` | `scripts/smoke-rest.sh` | Project and keys for the REST smoke test |
| `FLAGWIRE_SKIP_OFFLINE` | `scripts/verify-must-haves.sh` | Set to `true` to skip the check that stops both servers |
| `CHROME_PATH` | `scripts/lighthouse.sh`, `scripts/verify-must-haves.mjs` | Chrome binary, default `/usr/bin/google-chrome` |
| `LIGHTHOUSE_VERSION`, `LIGHTHOUSE_MIN_ACCESSIBILITY`, `LIGHTHOUSE_DEBUG_PORT` | `scripts/lighthouse.sh` | Lighthouse version (13.5.0), accessibility floor (95) and the debugging port of the Chrome it starts (19222) |
