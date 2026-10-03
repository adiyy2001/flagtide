# 0011 How propagation time is measured

Status: accepted, 2026-10-03

## Context

The target is p95 under 300 ms from commit to client acknowledgement with 5,000 clients, and the admin shows p50 and p95 live. A number without a definition is worth nothing, and clocks on different machines disagree.

## Decision

- `committedAt` is taken by the writing instance at the end of the transaction body, just before the commit, and stored in the change log entry. It is carried to clients as `committedAtMs` (epoch milliseconds computed from the `ZonedDateTime`). The error against the real commit is small and one-sided.
- Clients send `ack` with the version they applied. The instance that holds the connection computes `ackTime - committedAt` with its own clock and records it. For a delta frame with several entries only the newest version is acknowledged and timed.
- Each instance keeps a histogram per environment (1 ms buckets up to 5 s plus an overflow bucket, 60 second window in 5 second slices) and the count of connected clients, and writes both to `instance_stats` once per second. The admin API merges the rows that were updated in the last 5 seconds and reads p50, p95 and p99 from the merged buckets.
- The k6 load test records `commit_to_receipt_ms` as the time a frame is received minus `committedAtMs`. It runs in a container on the same host as the servers, so all clocks are the same clock.
- Every benchmark script prints the hardware and software it ran on, and every number in the README comes from such a script.

## Alternatives

- Round trip time measured at the client: includes the client's own clock and the return trip, and the server cannot show it.
- Database `clock_timestamp()` as commit time: still not the commit instant, and it adds a query.
- Averages: hide the tail that the target is about.

## Consequences

- Across machines the numbers include clock skew. The documentation says the measurements were taken on one host.
- Merging fixed-width histograms is exact for the bucket resolution, which is why they are used instead of keeping raw samples.
