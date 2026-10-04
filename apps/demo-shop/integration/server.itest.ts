import { spawn } from 'node:child_process';
import type { ChildProcess } from 'node:child_process';
import { createServer } from 'node:http';
import type { Server } from 'node:http';
import { createServer as createNetServer } from 'node:net';
import { resolve } from 'node:path';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { booleanFlag, variantFlag } from '../../../libs/core/test-support/fixtures';

const SERVER_ENTRY = resolve(import.meta.dirname, '../../../dist/apps/demo-shop/server/server.mjs');
const SDK_KEY = 'fws_integration';

interface Requests {
  readonly authorization: (string | undefined)[];
}

function freePort(): Promise<number> {
  return new Promise((done, fail) => {
    const probe = createNetServer();
    probe.once('error', fail);
    probe.listen(0, '127.0.0.1', () => {
      const address = probe.address();
      const port = typeof address === 'object' && address !== null ? address.port : 0;
      probe.close(() => done(port));
    });
  });
}

function listen(server: Server, port: number): Promise<void> {
  return new Promise((done) => server.listen(port, '127.0.0.1', done));
}

function snapshotBody(betaOn: boolean): string {
  return JSON.stringify({
    v: 7,
    committedAtMs: 1,
    flags: [
      booleanFlag('promo-banner'),
      variantFlag(
        'promo',
        'json',
        [{ key: 'p', value: { headline: 'Rendered on the server', code: 'SSR10', discountPercent: 10 } }],
        'p',
      ),
      variantFlag('checkout-label', 'string', [{ key: 'a', value: 'Buy now' }], 'a'),
      variantFlag('free-shipping-threshold', 'number', [{ key: 'a', value: 75 }], 'a'),
      booleanFlag('beta-recommendations', { enabled: betaOn }),
    ],
    segments: [],
  });
}

async function waitUntilListening(base: string): Promise<void> {
  for (let attempt = 0; attempt < 100; attempt += 1) {
    const reachable = await fetch(`${base}/healthz`).then(
      (response) => response.ok,
      () => false,
    );
    if (reachable) {
      return;
    }
    await new Promise((done) => setTimeout(done, 100));
  }
  throw new Error('the shop server did not start');
}

describe('demo shop server', () => {
  let api: Server;
  let shop: ChildProcess;
  let base: string;
  let betaOn = false;
  let apiDown = false;
  const requests: Requests = { authorization: [] };

  beforeAll(async () => {
    const apiPort = await freePort();
    api = createServer((request, response) => {
      requests.authorization.push(request.headers.authorization);
      if (apiDown || request.url !== '/sdk/v1/snapshot') {
        response.statusCode = 503;
        response.end();
        return;
      }
      response.setHeader('Content-Type', 'application/json');
      response.end(snapshotBody(betaOn));
    });
    await listen(api, apiPort);
    const port = await freePort();
    base = `http://127.0.0.1:${port}`;
    shop = spawn('node', [SERVER_ENTRY], {
      env: {
        ...process.env,
        PORT: String(port),
        HOST: '127.0.0.1',
        FLAGTIDE_SHOP_SDK_KEY: SDK_KEY,
        FLAGTIDE_SHOP_STREAM_URL: 'ws://browser-facing:18082/sdk/v1/stream',
        FLAGTIDE_SHOP_SNAPSHOT_URL: `http://127.0.0.1:${apiPort}`,
        FLAGTIDE_SHOP_FRAME_ANCESTORS: "'self' http://harness.test",
      },
      stdio: 'ignore',
    });
    await waitUntilListening(base);
  });

  afterAll(async () => {
    shop.kill();
    await new Promise((done) => api.close(done));
  });

  it('renders flag gated content on the server using the snapshot from the api', async () => {
    const response = await fetch(`${base}/`);
    const html = await response.text();
    expect(response.status).toBe(200);
    expect(html).toContain('Rendered on the server');
    expect(html).toContain('SSR10');
    expect(html).toContain('Buy now');
    expect(requests.authorization).toContain(`Bearer ${SDK_KEY}`);
  });

  it('hands the snapshot to the browser through the transfer state', async () => {
    const html = await (await fetch(`${base}/`)).text();
    const state = /<script id="ng-state" type="application\/json">([^<]*)<\/script>/u.exec(html);
    const transferred = JSON.parse(state?.[1] ?? '{}') as Record<string, { version: number }>;
    expect(transferred['flagtide:snapshot']?.version).toBe(7);
  });

  it('renders the beta link only for visitors the flag serves', async () => {
    betaOn = false;
    expect(await (await fetch(`${base}/`)).text()).not.toContain('nav-recommendations');
    betaOn = true;
    expect(await (await fetch(`${base}/`)).text()).toContain('nav-recommendations');
    betaOn = false;
  });

  it('redirects the beta route to the shop while the flag is off and serves it when on', async () => {
    const redirected = await fetch(`${base}/recommendations`, { redirect: 'manual' });
    expect(redirected.status).toBe(302);
    expect(redirected.headers.get('location')).toBe('/');
    betaOn = true;
    const served = await fetch(`${base}/recommendations`);
    expect(await served.text()).toContain('Recommended for you');
    betaOn = false;
  });

  it('renders the fallbacks instead of failing when the api is down', async () => {
    apiDown = true;
    const response = await fetch(`${base}/`);
    const html = await response.text();
    apiDown = false;
    expect(response.status).toBe(200);
    expect(html).not.toContain('Rendered on the server');
    expect(html).toContain('Checkout');
  });

  it('serves config.json for the browser without the internal snapshot address', async () => {
    const response = await fetch(`${base}/config.json`);
    expect(response.headers.get('cache-control')).toBe('no-store');
    expect(await response.json()).toEqual({
      sdkKey: SDK_KEY,
      streamUrl: 'ws://browser-facing:18082/sdk/v1/stream',
    });
  });

  it('answers the health check', async () => {
    const response = await fetch(`${base}/healthz`);
    expect(await response.json()).toEqual({ status: 'ok' });
  });

  it('sets security headers and lets only the configured origin frame the shop', async () => {
    const response = await fetch(`${base}/`);
    expect(response.headers.get('content-security-policy')).toBe(
      "frame-ancestors 'self' http://harness.test",
    );
    expect(response.headers.get('x-content-type-options')).toBe('nosniff');
    expect(response.headers.get('referrer-policy')).toBe('no-referrer');
  });

  it('serves hashed bundles with a long cache and cannot be walked out of its folder', async () => {
    const html = await (await fetch(`${base}/`)).text();
    const bundle = /href="(styles-[A-Z0-9]+\.css)"/u.exec(html)?.[1];
    expect(bundle).toBeDefined();
    const asset = await fetch(`${base}/${bundle}`);
    expect(asset.status).toBe(200);
    expect(asset.headers.get('cache-control')).toBe('public, max-age=31536000, immutable');
    const escape = await fetch(`${base}/..%2F..%2Fserver%2Fserver.mjs`);
    expect(await escape.text()).not.toContain('createNodeRequestHandler');
  });

  it('shows a not found page with status 404 for unknown paths', async () => {
    const response = await fetch(`${base}/nowhere`);
    expect(response.status).toBe(404);
    expect(await response.text()).toContain('Page not found');
  });
});
