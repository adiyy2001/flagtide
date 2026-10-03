# @flagwire/angular

Signals-first feature flags for Angular. Flags are evaluated in the browser with the same algorithm as the flagwire server and update when the server pushes a change. It works zoneless and with server side rendering.

```ts
import { ApplicationConfig } from '@angular/core';
import { provideFlagwire } from '@flagwire/angular';

export const appConfig: ApplicationConfig = {
  providers: [
    provideFlagwire({
      sdkKey: 'fws_...',
      streamUrl: 'wss://flags.example.com/sdk/v1/stream',
      context: { key: 'user-42', attributes: { plan: 'pro' } },
    }),
  ],
};
```

## Reading flags

```ts
import { Component } from '@angular/core';
import { injectFlag } from '@flagwire/angular';

@Component({
  selector: 'app-header',
  template: `
    @if (showBanner()) {
      <p>{{ bannerText() }}</p>
    }
  `,
})
export class HeaderComponent {
  readonly showBanner = injectFlag('header-banner', false);
  readonly bannerText = injectFlag('banner-text', 'Welcome');
}
```

`injectFlag(key, fallback)` returns a `Signal`. The fallback is used while the flag is unknown and when its type differs from the type of the fallback. Boolean, string, number and JSON flags are supported.

## Template and router

```html
<app-promo *flagwireFlag="'promo'; else plain" />
<ng-template #plain><app-standard /></ng-template>
<span *flagwireFlag="'checkout-label'; equals: 'Buy now'">Buy now</span>
```

```ts
import { flagwireGuard } from '@flagwire/angular';

export const routes = [
  {
    path: 'recommendations',
    canMatch: [flagwireGuard('beta-recommendations', { redirectTo: '/' })],
    loadComponent: () => import('./recommendations').then((m) => m.Recommendations),
  },
];
```

## Connection status

Inject `FlagwireStatus` and read `status()`: `connecting`, `live`, `stale` or `offline`. `stale` means flags are served from storage or after a dropped connection and cannot be confirmed current.

## Server side rendering

On the server the SDK opens no socket and touches no browser storage. It fetches the snapshot over HTTP before the first render and passes it to the browser through `TransferState`, so the first client render matches the server render. Set `snapshotUrl` when the server reaches the API under another address than the browser, and give `context` a key that is the same on both sides if you use percentage rollouts.

## Dev overrides panel

```ts
import { FlagwireOverridesPanel } from '@flagwire/angular/overrides';
```

Add `<flagwire-overrides-panel />` once, for example in development builds. It lists every flag, lets you override values in this browser and reports them with the reason `OVERRIDE`.

## Requirements

Angular 22, `@flagwire/core` and RxJS 7.8 as peer dependencies.

MIT licensed.
