import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { FakeStreamServer } from '../../test-support/fake-server.js';
import { booleanFlag, upsertFlag, variantFlag } from '../../test-support/fixtures.js';
import { createFlagwireClient } from './client.js';
import type { FlagwireClient } from './client.js';
import { createMemoryStore } from './storage.js';

async function until(condition: () => boolean, timeoutMs = 3000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (!condition()) {
    if (Date.now() > deadline) {
      throw new Error('condition was not met in time');
    }
    await new Promise((resolve) => setTimeout(resolve, 5));
  }
}

describe('stream against a fake server', () => {
  let server: FakeStreamServer;
  const clients: FlagwireClient[] = [];

  beforeEach(async () => {
    server = await FakeStreamServer.start(
      [booleanFlag('checkout', { fallthrough: { variant: 'off' } })],
      [],
      4,
    );
  });

  afterEach(async () => {
    clients.splice(0).forEach((client) => client.stop());
    await server.stop();
  });

  function connect(overrides: Partial<Parameters<typeof createFlagwireClient>[0]> = {}): FlagwireClient {
    const client = createFlagwireClient({
      streamUrl: overrides.streamUrl ?? server.url,
      sdkKey: 'fws_test',
      context: { key: 'user-1', attributes: {} },
      store: createMemoryStore(),
      timing: { backoffBaseMs: 10, backoffCapMs: 40 },
      ...overrides,
    });
    clients.push(client);
    client.start();
    return client;
  }

  it('sends hello, receives the snapshot, evaluates locally and acknowledges', async () => {
    const client = connect();
    await until(() => client.status === 'live');
    expect(client.version).toBe(4);
    expect(client.value('checkout', true)).toBe(false);
    expect(server.hellos[0]).toMatchObject({ t: 'hello', sdkKey: 'fws_test', clientId: 'user-1' });
    expect(server.hellos[0]).not.toHaveProperty('version');
    await until(() => server.acks.includes(4));
  });

  it('applies a pushed delta, announces the change and acknowledges the new version', async () => {
    const client = connect();
    await until(() => client.status === 'live');
    let announced = 0;
    client.changes$.subscribe(() => (announced += 1));
    server.publish(upsertFlag(booleanFlag('checkout')));
    await until(() => client.value('checkout', false));
    expect(client.version).toBe(5);
    expect(announced).toBe(1);
    await until(() => server.acks.includes(5));
  });

  it('reconnects after a drop and receives exactly the deltas it missed', async () => {
    const client = connect();
    await until(() => client.status === 'live');
    server.dropConnections();
    server.publishSilently(upsertFlag(variantFlag('banner', 'string', [{ key: 'a', value: 'sale' }], 'a')));
    server.publishSilently(upsertFlag(booleanFlag('checkout')));
    await until(() => client.version === 6);
    expect(server.hellos).toHaveLength(2);
    expect(server.hellos[1]).toMatchObject({ version: 4 });
    expect(client.value('banner', '')).toBe('sale');
    expect(client.value('checkout', false)).toBe(true);
    expect(client.status).toBe('live');
  });

  it('falls back to a snapshot when the server no longer retains the missed history', async () => {
    const client = connect();
    await until(() => client.status === 'live');
    server.dropConnections();
    server.publishSilently(upsertFlag(booleanFlag('checkout')));
    server.forgetHistoryBefore(100);
    await until(() => client.version === 5);
    expect(client.value('checkout', false)).toBe(true);
  });

  it('treats a silent server as dead and reconnects', async () => {
    server.sendHeartbeatsEvery(20);
    const client = connect({ timing: { backoffBaseMs: 10, backoffCapMs: 40, staleAfterMs: 120 } });
    await until(() => client.status === 'live');
    server.sendHeartbeatsEvery(null);
    const statuses: string[] = [];
    client.status$.subscribe((status) => statuses.push(status));
    await until(() => statuses.includes('stale'));
    await until(() => server.hellos.length >= 2);
  });

  it('stops and reports when the key is rejected', async () => {
    server.rejectKeysOtherThan('someone-else');
    const client = connect();
    const errors: number[] = [];
    client.errors$.subscribe((error) => errors.push(error.code));
    await until(() => errors.includes(4401) && client.status === 'offline');
    const hellosWhenStopped = server.hellos.length;
    await new Promise((resolve) => setTimeout(resolve, 150));
    expect(server.hellos).toHaveLength(hellosWhenStopped);
    expect(client.status).toBe('offline');
  });

  it('starts from the stored snapshot while the server is unreachable', async () => {
    const store = createMemoryStore();
    const first = connect({ store });
    await until(() => first.status === 'live');
    first.stop();

    const offlineStart = connect({ store, streamUrl: 'ws://127.0.0.1:1/sdk/v1/stream' });
    expect(offlineStart.version).toBe(4);
    expect(offlineStart.value('checkout', true)).toBe(false);
    expect(offlineStart.status).toBe('stale');
  });
});
