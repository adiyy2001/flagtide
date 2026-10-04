import '@angular/compiler';
import { Component, provideZonelessChangeDetection } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideServerRendering, renderApplication } from '@angular/platform-server';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { booleanFlag, variantFlag } from '../../core/test-support/fixtures';
import { injectFlag, provideFlagtide } from '../src/index';

@Component({
  selector: 'flagtide-test-root',
  template: `
    <h1>{{ label() }}</h1>
    @if (banner()) {
      <p class="banner">banner on</p>
    }
  `,
})
class AppComponent {
  readonly label = injectFlag('label', 'fallback');
  readonly banner = injectFlag('banner', false);
}

const SDK_FRAME = /libs\/(core|angular)\/(overrides\/)?src\//u;

const forbiddenGlobals = ['window', 'document', 'localStorage', 'sessionStorage', 'WebSocket'] as const;

describe('server side rendering', () => {
  const touched: string[] = [];
  const requests: { url: string; authorization: string | undefined }[] = [];

  beforeEach(() => {
    touched.length = 0;
    requests.length = 0;
    forbiddenGlobals.forEach((name) => {
      Object.defineProperty(globalThis, name, {
        configurable: true,
        get: () => {
          if (SDK_FRAME.test(new Error().stack ?? '')) {
            touched.push(name);
            throw new Error(`${name} must not be touched by the SDK on the server`);
          }
          return undefined;
        },
      });
    });
  });

  afterEach(() => {
    forbiddenGlobals.forEach((name) => {
      Reflect.deleteProperty(globalThis, name);
    });
    vi.unstubAllGlobals();
  });

  function stubFetch(status = 200): void {
    const snapshot = {
      v: 12,
      committedAtMs: 1,
      flags: [
        booleanFlag('banner'),
        variantFlag('label', 'string', [{ key: 'a', value: 'Rendered on the server' }], 'a'),
      ],
      segments: [],
    };
    vi.stubGlobal('fetch', (url: string, init: { headers: Record<string, string> }) => {
      requests.push({ url, authorization: init.headers['Authorization'] });
      return Promise.resolve({
        ok: status === 200,
        status,
        text: () => Promise.resolve(JSON.stringify(snapshot)),
      });
    });
  }

  async function render(): Promise<string> {
    return renderApplication(
      (context) =>
        bootstrapApplication(
          AppComponent,
          {
            providers: [
              provideZonelessChangeDetection(),
              provideServerRendering(),
              provideFlagtide({
                sdkKey: 'fws_server',
                streamUrl: 'ws://browser-facing:8080/sdk/v1/stream',
                snapshotUrl: 'http://api:8080',
                context: { key: 'visitor-1', attributes: {} },
              }),
            ],
          },
          context,
        ),
      {
        document: '<html><head></head><body><flagtide-test-root></flagtide-test-root></body></html>',
        url: '/',
      },
    );
  }

  it('renders flag gated content with the snapshot fetched over http and carries it in TransferState', async () => {
    stubFetch();
    const html = await render();
    expect(html).toContain('Rendered on the server');
    expect(html).toContain('banner on');
    expect(requests).toEqual([
      { url: 'http://api:8080/sdk/v1/snapshot', authorization: 'Bearer fws_server' },
    ]);
    const state = /<script id="ng-state" type="application\/json">([^<]*)<\/script>/u.exec(html);
    expect(state).not.toBeNull();
    const transferred = JSON.parse(state?.[1] ?? '{}') as Record<
      string,
      { version: number; flags: { key: string }[] }
    >;
    expect(transferred['flagtide:snapshot']?.version).toBe(12);
    expect(transferred['flagtide:snapshot']?.flags.map((flag) => flag.key)).toEqual(['banner', 'label']);
  });

  it('never touches window, document, localStorage, sessionStorage or WebSocket', async () => {
    stubFetch();
    await render();
    expect(touched).toEqual([]);
  });

  it('renders the fallbacks when the api is unreachable instead of failing the render', async () => {
    stubFetch(503);
    const html = await render();
    expect(html).toContain('fallback');
    expect(html).not.toContain('banner on');
    expect(touched).toEqual([]);
  });
});
