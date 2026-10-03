import { describe, expect, it } from 'vitest';
import {
  browserConfig,
  DEFAULT_SHOP_CONFIG,
  loadShopConfig,
  parseShopConfig,
  shopConfigFromEnv,
} from './shop-config';

describe('shopConfigFromEnv', () => {
  it('uses the defaults without variables and treats blanks as unset', () => {
    expect(shopConfigFromEnv({})).toEqual(DEFAULT_SHOP_CONFIG);
    expect(shopConfigFromEnv({ FLAGWIRE_SHOP_SDK_KEY: '  ' })).toEqual(DEFAULT_SHOP_CONFIG);
  });

  it('reads the three variables', () => {
    expect(
      shopConfigFromEnv({
        FLAGWIRE_SHOP_SDK_KEY: 'k',
        FLAGWIRE_SHOP_STREAM_URL: 'ws://b/sdk/v1/stream',
        FLAGWIRE_SHOP_SNAPSHOT_URL: 'http://server-b:8080',
      }),
    ).toEqual({ sdkKey: 'k', streamUrl: 'ws://b/sdk/v1/stream', snapshotUrl: 'http://server-b:8080' });
  });

  it('keeps the internal snapshot address out of what the browser receives', () => {
    expect(browserConfig(shopConfigFromEnv({ FLAGWIRE_SHOP_SNAPSHOT_URL: 'http://internal' }))).toEqual({
      sdkKey: DEFAULT_SHOP_CONFIG.sdkKey,
      streamUrl: DEFAULT_SHOP_CONFIG.streamUrl,
    });
  });
});

describe('parseShopConfig', () => {
  it('accepts ws and wss stream urls', () => {
    expect(parseShopConfig({ sdkKey: 'k', streamUrl: 'wss://x/sdk/v1/stream' }).streamUrl).toBe(
      'wss://x/sdk/v1/stream',
    );
  });

  it.each([
    [null, 'object'],
    [[], 'object'],
    [{ streamUrl: 'ws://x' }, 'sdkKey'],
    [{ sdkKey: 'k', streamUrl: 'http://x' }, 'streamUrl'],
  ])('rejects %j', (raw, mention) => {
    expect(() => parseShopConfig(raw)).toThrow(mention);
  });
});

describe('loadShopConfig', () => {
  it('loads and parses the file', async () => {
    const fetcher = (() =>
      Promise.resolve(Response.json({ sdkKey: 'k', streamUrl: 'ws://x/s' }))) as unknown as typeof fetch;
    expect(await loadShopConfig('config.json', fetcher)).toEqual({ sdkKey: 'k', streamUrl: 'ws://x/s' });
  });

  it('fails when the file is missing', async () => {
    const fetcher = (() => Promise.resolve(new Response('', { status: 404 }))) as unknown as typeof fetch;
    await expect(loadShopConfig('config.json', fetcher)).rejects.toThrow('404');
  });
});
