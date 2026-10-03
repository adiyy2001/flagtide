import { describe, expect, it } from 'vitest';
import { booleanFlag } from '../../test-support/fixtures.js';
import { fetchSnapshot } from './fetch-snapshot.js';
import type { FetchLike } from './fetch-snapshot.js';

function respond(
  status: number,
  body: string,
): { fetch: FetchLike; calls: { url: string; headers: Record<string, string> }[] } {
  const calls: { url: string; headers: Record<string, string> }[] = [];
  const fetch: FetchLike = (url, init) => {
    calls.push({ url, headers: { ...init.headers } });
    return Promise.resolve({ ok: status >= 200 && status < 300, status, text: () => Promise.resolve(body) });
  };
  return { fetch, calls };
}

describe('fetchSnapshot', () => {
  it('requests the snapshot with the SDK key and returns it', async () => {
    const body = JSON.stringify({ v: 12, committedAtMs: 1, flags: [booleanFlag('a')], segments: [] });
    const { fetch, calls } = respond(200, body);
    const snapshot = await fetchSnapshot('http://server:8080', 'fws_key', fetch);
    expect(calls[0]?.url).toBe('http://server:8080/sdk/v1/snapshot');
    expect(calls[0]?.headers['Authorization']).toBe('Bearer fws_key');
    expect(snapshot).toMatchObject({ version: 12 });
    expect(snapshot.flags).toHaveLength(1);
  });

  it('fails on an error status', async () => {
    await expect(fetchSnapshot('http://s', 'k', respond(401, '{}').fetch)).rejects.toThrow('401');
  });

  it('fails when the body is not a snapshot', async () => {
    await expect(fetchSnapshot('http://s', 'k', respond(200, '{"x":1}').fetch)).rejects.toThrow('snapshot');
  });
});
