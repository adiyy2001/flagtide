import { describe, expect, it } from 'vitest';
import { FakeEnvironment, FakeSockets } from '../../test-support/fake-socket.js';
import { booleanFlag, snapshotFrame, variantFlag } from '../../test-support/fixtures.js';
import { createFlagwireClient } from './client.js';
import type { FlagwireClientOptions } from './client.js';
import { createMemoryStore, createSnapshotStorage } from './storage.js';

function build(overrides: Partial<FlagwireClientOptions> = {}) {
  const sockets = new FakeSockets();
  const client = createFlagwireClient({
    streamUrl: 'ws://test',
    sdkKey: 'fws_test',
    store: createMemoryStore(),
    socketFactory: sockets.factory,
    environment: new FakeEnvironment(),
    ...overrides,
  });
  return { client, sockets };
}

describe('FlagwireClient', () => {
  it('does not open a socket until it is started', () => {
    const { client, sockets } = build();
    expect(sockets.sockets).toHaveLength(0);
    client.start();
    expect(sockets.sockets).toHaveLength(1);
    client.stop();
  });

  it('returns fallbacks before any data arrived', () => {
    const { client } = build();
    expect(client.resolve('missing', true)).toMatchObject({ value: true, reason: 'FLAG_NOT_FOUND' });
    expect(client.version).toBeNull();
    expect(client.snapshot()).toBeNull();
  });

  it('evaluates against the context and reacts to a context change', () => {
    const rolloutFlag = booleanFlag('checkout', {
      rules: [
        {
          id: 'beta',
          conditions: [{ attribute: 'plan', operator: 'equals', values: ['pro'] }],
          serve: { variant: 'on' },
        },
      ],
      fallthrough: { variant: 'off' },
    });
    const { client, sockets } = build({ context: { key: 'u1', attributes: { plan: 'free' } } });
    client.start();
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(1, [rolloutFlag]));
    let announced = 0;
    client.changes$.subscribe(() => (announced += 1));
    expect(client.value('checkout', true)).toBe(false);
    client.setContext({ key: 'u1', attributes: { plan: 'pro' } });
    expect(client.value('checkout', false)).toBe(true);
    expect(client.resolve('checkout', false)).toMatchObject({ reason: 'RULE_MATCH', ruleId: 'beta' });
    expect(announced).toBe(1);
    expect(client.context.attributes).toEqual({ plan: 'pro' });
    client.stop();
  });

  it('lists flag keys and types', () => {
    const { client, sockets } = build();
    client.start();
    sockets.latest.open();
    sockets.latest.receive(
      snapshotFrame(1, [booleanFlag('a'), variantFlag('b', 'number', [{ key: 'x', value: 5 }], 'x')]),
    );
    expect(client.flagKeys()).toEqual(['a', 'b']);
    expect(client.flagType('b')).toBe('number');
    expect(client.flagType('nope')).toBeUndefined();
    client.stop();
  });

  it('applies overrides, announces them and persists them', () => {
    const store = createMemoryStore();
    const first = build({ store });
    first.client.start();
    first.sockets.latest.open();
    first.sockets.latest.receive(snapshotFrame(1, [booleanFlag('a', { fallthrough: { variant: 'off' } })]));
    let announced = 0;
    first.client.changes$.subscribe(() => (announced += 1));
    first.client.overrides.set('a', true);
    expect(first.client.resolve('a', false)).toMatchObject({ value: true, reason: 'OVERRIDE' });
    expect(announced).toBe(1);
    first.client.stop();

    const second = build({ store });
    expect(second.client.resolve('a', false).reason).toBe('OVERRIDE');
  });

  it('keeps an anonymous id between clients that share storage', () => {
    const store = createMemoryStore();
    const first = build({ store }).client.context.key;
    const second = build({ store }).client.context.key;
    expect(first).toBe(second);
    expect(first.length).toBeGreaterThan(8);
  });

  it('persists every snapshot and starts from it next time, marked stale', () => {
    const store = createMemoryStore();
    const first = build({ store });
    first.client.start();
    first.sockets.latest.open();
    first.sockets.latest.receive(snapshotFrame(7, [booleanFlag('a')]));
    first.client.stop();
    expect(createSnapshotStorage(store, 'fws_test').load()?.version).toBe(7);

    const second = build({ store });
    expect(second.client.version).toBe(7);
    expect(second.client.status).toBe('stale');
    expect(second.client.value('a', false)).toBe(true);
  });

  it('prefers a given initial snapshot over the stored one', () => {
    const store = createMemoryStore();
    createSnapshotStorage(store, 'fws_test').save({ version: 2, flags: [booleanFlag('old')], segments: [] });
    const { client } = build({
      store,
      initialSnapshot: { version: 9, flags: [booleanFlag('new')], segments: [] },
    });
    expect(client.version).toBe(9);
    expect(client.flagKeys()).toEqual(['new']);
  });

  it('persists a given initial snapshot so the next start works without the server', () => {
    const store = createMemoryStore();
    build({ store, initialSnapshot: { version: 9, flags: [booleanFlag('new')], segments: [] } });
    const next = build({ store });
    expect(next.client.version).toBe(9);
    expect(next.client.value('new', false)).toBe(true);
  });

  it('hydrates from a snapshot and announces it', () => {
    const { client } = build();
    let announced = 0;
    client.changes$.subscribe(() => (announced += 1));
    client.hydrate({ version: 3, flags: [booleanFlag('a')], segments: [] });
    expect(client.version).toBe(3);
    expect(client.value('a', false)).toBe(true);
    expect(announced).toBe(1);
  });

  it('exposes the connection errors and status stream', () => {
    const { client, sockets } = build();
    const statuses: string[] = [];
    const codes: number[] = [];
    client.status$.subscribe((status) => statuses.push(status));
    client.errors$.subscribe((error) => codes.push(error.code));
    client.start();
    sockets.latest.closeFromServer(4401);
    expect(statuses).toEqual(['connecting', 'offline']);
    expect(codes).toEqual([4401]);
  });
});
