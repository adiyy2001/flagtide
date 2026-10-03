# Stream protocol

Version 1. The stream endpoint is `GET /sdk/v1/stream`, upgraded to a WebSocket. Every message is a JSON text frame with a `t` field naming its type. The reasoning behind the choices is in [ADR 0010](adr/0010-wire-protocol-and-auth.md) and [ADR 0011](adr/0011-propagation-measurement.md).

## Session

1. The client opens the socket. A browser cannot set headers, so the SDK key never goes into the URL.
2. The client sends `hello` within 5 seconds.
3. The server answers with exactly one `snapshot` or one `deltas` frame.
4. From then on the server sends a `deltas` frame for every change and an `hb` frame every 15 seconds.
5. The client sends `ack` after it has applied a frame.

The server closes the socket on the first protocol violation. An `error` frame goes out before the close frame whenever the socket is still writable.

## Client to server

| Frame | Fields | Notes |
| --- | --- | --- |
| `hello` | `sdkKey` (string), `version` (integer, optional), `clientId` (string, optional), `sdk` (string, optional) | Must be the first frame and may be sent once. Without `version` the client gets a snapshot. |
| `ack` | `v` (integer) | The version the client applied. Only valid after `hello`. |

A frame longer than 4096 characters is a bad frame. The `sdkKey` selects the environment, so a socket can only ever see one environment.

## Server to client

| Frame | Fields |
| --- | --- |
| `snapshot` | `v` (integer), `committedAtMs` (integer, absent when the environment has no change yet), `flags` (array of flag configs), `segments` (array of segments) |
| `deltas` | `from` (integer), `to` (integer), `entries` (array, oldest first) |
| `hb` | `ts` (epoch milliseconds on the server), `v` (latest version of the environment) |
| `error` | `code` (integer, same as the close code), `message` (string) |

A `deltas` entry has `v`, `committedAtMs` and `changes`. A change has `op` (`upsert` or `remove`), `kind` (`flag` or `segment`), `key`, and for upserts `config`. A `snapshot` replaces everything the client holds. `deltas` are applied in order on top of version `from`.

```json
{"t":"deltas","from":41,"to":42,"entries":[{"v":42,"committedAtMs":1790000000123,"changes":[{"op":"upsert","kind":"flag","key":"new-checkout","config":{}}]}]}
```

### Which frame answers `hello`

| Situation | Answer |
| --- | --- |
| No `version` | `snapshot` |
| `version` equals the current version | `deltas` with `from` = `to` and no entries |
| `version` is older than the current one and the retained window still covers `version + 1` | `deltas` with exactly the missed entries |
| `version` is older than the retained window | `snapshot` |
| `version` is ahead of the server (a restored database, another environment) | `snapshot` |

The window is the per-environment ring of recent change log entries held in memory (1000 by default, `flagwire.propagation.ring-capacity`). When the ring cannot cover a client, the answer is a snapshot, which is built once per version and shared by every client that needs it.

## Close codes

| Code | Meaning |
| --- | --- |
| 4400 | Bad frame: not JSON, unknown `t`, missing field, too long, or a second `hello` |
| 4401 | The SDK key is unknown |
| 4403 | The `Origin` header is not allowed (the upgrade is refused with HTTP 403 before the socket exists) |
| 4408 | No `hello` arrived within the deadline |
| 4429 | The client does not keep up: too many frames waiting to be written, or too many sockets waiting for a `hello` |
| 1013 | The server failed while handling the socket or could not reach its storage, try again later |

## Acknowledgements

The instance that holds the connection measures `ack time - committedAtMs` with its own clock and records it in the propagation histogram of the environment. A `deltas` frame with several entries is acknowledged with the newest version, and only that version is timed. The merged histogram is served by `GET /api/v1/projects/{project}/environments/{environment}/propagation`.

## Connection status in the SDK

| Status | Meaning |
| --- | --- |
| `connecting` | The first attempt is in progress and nothing is confirmed yet |
| `live` | The socket is open and the data is confirmed current by a snapshot, deltas or a heartbeat in the last 40 seconds |
| `stale` | Flags are served but cannot be confirmed current: they come from storage, heartbeats stopped, or a retry is running after the stream was live |
| `offline` | The browser reports no network, or the server stayed unreachable beyond the limit |

The client treats 40 seconds without any frame as a dead connection. It compares timestamps instead of counting timer ticks, because background tabs throttle timers, and it checks again on `visibilitychange`.

## Reconnect

`delay = random(0, min(30 s, 500 ms * 2^attempt))`, full jitter. The attempt counter resets once a frame has been received on the new connection. Every reconnect sends `hello` with the last applied version.

| Constant | Value |
| --- | --- |
| Backoff base | 500 ms |
| Backoff cap | 30 s |
| Heartbeat interval | 15 s |
| Stale after | 40 s without a frame |
| Hello deadline | 5 s |

## Instances

Every instance consumes the same PostgreSQL `LISTEN` channel and the writer is no exception, so there is one code path for fan-out. The notification carries a pointer only (environment and version), never the change itself, because a payload is limited to 8000 bytes and is not durable. An instance that learns about a newer version reads the missing entries from `change_log`. After every listener reconnect it reads again for all environments it serves, so a lost notification costs time and never data.
