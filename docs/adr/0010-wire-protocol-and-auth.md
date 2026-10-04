# 0010 Wire protocol, authentication and connection status

Status: accepted, 2026-10-03

## Context

A browser WebSocket cannot set request headers. The Quarkus WebSockets Next guide covers authentication through headers and a sub-protocol workaround, and it has no convenient access to query parameters. Query strings also end up in proxy logs. The client needs to know whether what it shows is current.

## Decision

- JSON text frames with a `t` field. Client to server: `hello` (`sdkKey`, optional `version`, `clientId`, `sdk`) and `ack` (`v`). Server to client: `snapshot`, `deltas` (ordered entries, each with version, commit time in epoch milliseconds and the changes), `hb`, `error`. Full definitions are in `docs/protocol.md`.
- The SDK key is sent in the first frame, never in the URL. The server closes with 4408 if no `hello` arrives within the deadline, 4401 for an unknown key, 4400 for a bad frame and 4429 for a consumer that cannot keep up.
- The server sends an application level `hb` every 15 seconds. Browsers answer ping frames on their own but JavaScript cannot see them, so a client could not detect a dead connection otherwise. The client treats 40 seconds without any frame as stale and reconnects.
- The server checks the `Origin` header against an allow-list.
- Reconnect: exponential backoff with full jitter, `random(0, min(30 s, 500 ms * 2^attempt))`, reset after the connection is live. The client sends its last applied version so the server can answer with deltas.
- Status signal values: `connecting` (a first attempt is in progress and nothing has been confirmed yet), `live` (the stream is open and its data is confirmed current), `stale` (flags are being served that cannot be confirmed current: a snapshot from storage, missed heartbeats, or a retry after having been live), `offline` (the browser reports no network, or the server stayed unreachable beyond a limit). The state machine is written down in `docs/protocol.md`.

## Alternatives

- Key in the query string: simplest, but it lands in logs.
- `Sec-WebSocket-Protocol` carrying the key: works and avoids the first frame, but abuses a header that is meant for sub-protocol negotiation.
- A binary format (MessagePack, Protobuf): smaller, harder to read in browser dev tools. JSON is enough here and easy to inspect. Compression can be added if the snapshot size shows up in the load test.
- Server-Sent Events: one way and simpler, but the design is built on WebSockets and acknowledgements flow back on the same socket.

## Consequences

- An unauthenticated socket can exist for a few seconds. The hello deadline and a limit on connections without a hello keep that cheap.
- Acknowledgements are what make the propagation monitor possible (ADR 0011).
