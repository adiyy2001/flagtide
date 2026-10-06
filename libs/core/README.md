# @flagtide/core

The framework-agnostic part of the flagtide SDK. It evaluates feature flags locally, with the same result as the Java server, and keeps them current over a WebSocket.

```sh
npm install @flagtide/core rxjs
```

```ts
import { createFlagtideClient } from '@flagtide/core';

const client = createFlagtideClient({
  streamUrl: 'wss://flags.example.com/sdk/v1/stream',
  sdkKey: 'fws_...',
  context: { key: 'user-42', attributes: { plan: 'pro', appVersion: '2.4.0' } },
});
client.start();

client.value('new-checkout', false);
client.resolve('banner-text', 'Welcome').reason;
client.status$.subscribe(console.log);
```

Evaluation reads the snapshot the client already holds, so it never waits for the network. A flag the client does not know, or one whose type differs from the fallback, returns the fallback with the reason `FLAG_NOT_FOUND` or `TYPE_MISMATCH`.

## Connection behavior

- The SDK key travels in the first frame, never in the URL.
- A reconnect sends the last applied version and receives exactly the changes it missed, or a full snapshot when it is too far behind.
- Reconnects use exponential backoff with full jitter: `random(0, min(30 s, 500 ms * 2^attempt))`.
- A connection that stays silent for 40 seconds is treated as dead. The check compares timestamps, so a throttled background tab does not cause false alarms, and it runs again when the tab becomes visible.
- The status is one of `connecting`, `live`, `stale` and `offline`.
- The last snapshot is kept in `localStorage` (when it is usable) so a page can start without the server.

## Server side rendering

Do not open a socket on a server. Fetch the snapshot with `fetchSnapshot(baseUrl, sdkKey)`, pass it as `initialSnapshot`, and call `start()` only in the browser.

## Evaluation without a connection

`evaluate`, `indexSegments`, `bucketOf` and `murmur3x86_32` are exported for code that wants the algorithm alone, for example a Node service or a test. The algorithm is specified in `docs/evaluation-spec.md` of the repository and checked against shared test vectors in both Java and TypeScript.

## Requirements

RxJS 7.8 or later as a peer dependency, and a runtime with `WebSocket` (browsers, Node 22 and later).

MIT licensed.
