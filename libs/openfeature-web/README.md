# @flagtide/openfeature-web-provider

An [OpenFeature](https://openfeature.dev) web provider for flagtide. Your code calls the vendor neutral OpenFeature API, and `@flagtide/core` does the work underneath: it evaluates flags in the browser with the same result as the Java server and keeps them current over a WebSocket.

```sh
npm install @flagtide/openfeature-web-provider @flagtide/core @openfeature/web-sdk rxjs
```

```ts
import { createFlagtideClient } from '@flagtide/core';
import { FlagtideWebProvider } from '@flagtide/openfeature-web-provider';
import { OpenFeature } from '@openfeature/web-sdk';

const flagtide = createFlagtideClient({
  streamUrl: 'wss://flags.example.com/sdk/v1/stream',
  sdkKey: 'fws_...',
});

await OpenFeature.setContext({ targetingKey: 'user-42', plan: 'pro', appVersion: '2.4.0' });
await OpenFeature.setProviderAndWait(new FlagtideWebProvider(flagtide));

const client = OpenFeature.getClient();
client.getBooleanValue('new-checkout', false);
client.getStringDetails('banner-text', 'Welcome').reason;
```

The provider starts the client itself and reports ready once the client holds flag data. With a stored snapshot or an `initialSnapshot` that happens at once; otherwise it waits for the first snapshot from the stream.

## Context

`targetingKey` becomes the flagtide context key, and every other field becomes an attribute your targeting rules can match on. Dates arrive as ISO 8601 strings. If there is no targeting key, the provider keeps the key the client started with: the anonymous id from storage, or the key from the context you gave the client. An empty OpenFeature context at startup leaves the client context alone.

Context changes apply synchronously, so evaluation never waits for the network.

## Resolution details

| flagtide           | OpenFeature reason                  | Notes                                                |
| ------------------ | ----------------------------------- | ---------------------------------------------------- |
| `RULE_MATCH`       | `TARGETING_MATCH`, `SPLIT`          | `SPLIT` when a percentage rollout picked the variant |
| `FALLTHROUGH`      | `DEFAULT`, `SPLIT`                  | `SPLIT` when a percentage rollout picked the variant |
| `OFF`, `KILL_SWITCH` | `DISABLED`                        |                                                      |
| `OVERRIDE`         | `OVERRIDE`                          | a local override from the flagtide dev panel         |
| `FLAG_NOT_FOUND`   | `ERROR` with `FLAG_NOT_FOUND`       | the default value is returned                        |
| `TYPE_MISMATCH`    | `ERROR` with `TYPE_MISMATCH`        | the default value is returned                        |

`variant` is the flagtide variant key. `flagMetadata` carries `ruleId` and `ruleIndex` when a rule matched and `bucket` when a rollout decided.

## Events

| Event                   | When                                                                                 |
| ----------------------- | ------------------------------------------------------------------------------------ |
| `PROVIDER_READY`        | flag data is available, again after the connection recovers                          |
| `PROVIDER_CONFIGURATION_CHANGED` | a snapshot, a change from the stream or a local override arrives          |
| `PROVIDER_STALE`        | the connection went stale or offline; evaluation keeps using the last data           |
| `PROVIDER_ERROR`        | no data arrived within `readyTimeoutMs` (10 s by default), or the SDK key was rejected |

A rejected SDK key is fatal. After a timeout the client keeps trying, and the provider reports ready as soon as data arrives.

```ts
new FlagtideWebProvider(flagtide, { readyTimeoutMs: 5_000 });
```

## Requirements

`@flagtide/core` 1.0 or later, `@openfeature/web-sdk` 1.5 or later and RxJS 7.8 or later as peer dependencies.

MIT licensed.
