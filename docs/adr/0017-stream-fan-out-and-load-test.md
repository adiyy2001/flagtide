# 0017 Stream fan-out and the load test

Status: accepted, 2026-10-03

## Context

A change has to reach several thousand sockets on two instances in well under a second, and one slow client must not hold the others back. The load test has to open 5,000 sockets on a laptop that also runs two JVMs and PostgreSQL.

## Decision

- Each instance has one propagation thread. A notification (or a resync after a listener reconnect) reads the missing entries from `change_log`, appends them to the per-environment ring and serializes the `deltas` frame once. Every connected socket of the environment receives the same string.
- Writes to a socket are asynchronous and counted. A connection with more than 256 frames waiting is closed with 4429 and the fan-out never blocks on it.
- A socket without a `hello` is closed after 5 seconds, and at most 20,000 sockets may wait for one.
- Snapshot answers are cached per environment version, so a reconnect storm builds one snapshot.
- The `hello` handling runs on a worker thread because it checks the key store. Everything after it stays on the event loop.
- The load test is a k6 script (`bench/k6/propagation.js`) with 50 virtual users holding 100 sockets each, split over both instances, and one trigger user that toggles a flag through the REST API of alternating instances once per second. It records `now - committedAtMs` for every delta frame. k6 runs from the official image inside the compose network, on the same host as the servers, so every timestamp comes from one clock. Alongside it the report stores what the instances measured from client acknowledgements.
- Instance identity comes from `FLAGWIRE_INSTANCE_ID` and defaults to a random UUID, which keeps rows in `instance_stats` apart.

## Alternatives

- One thread per environment: more parallelism, but the work per notification is a single query and a string, and the fan-out is dominated by socket writes.
- Serializing a frame per client: simpler, and several times more allocation at 5,000 clients.
- One k6 virtual user per socket: 5,000 virtual users do not fit in the memory budget of the machine.

## Consequences

- The numbers depend on the host. The result file records the hardware, and runs on a shared machine vary from run to run.
- The ring bounds what a reconnecting client can receive as deltas. Older clients get a snapshot, which is the intended behaviour and is covered by a test.
