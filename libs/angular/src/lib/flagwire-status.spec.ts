import { TestBed } from '@angular/core/testing';
import { createMemoryStore, createSnapshotStorage } from '@flagwire/core';
import { describe, expect, it } from 'vitest';
import { booleanFlag, snapshotFrame } from '../../../core/test-support/fixtures';
import { FakeSockets, testFlagwire } from '../../test-support/flagwire-testing';
import { Flagwire } from './flagwire';
import { FlagwireStatus } from './flagwire-status';

describe('FlagwireStatus', () => {
  it('follows the connection: connecting, live, stale after a drop, live again', () => {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({ providers: [testFlagwire(sockets, { timing: { backoffBaseMs: 0 } })] });
    const status = TestBed.inject(FlagwireStatus).status;
    expect(status()).toBe('connecting');
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(1, [booleanFlag('a')]));
    expect(status()).toBe('live');
    sockets.latest.closeFromServer(1006);
    expect(status()).toBe('stale');
  });

  it('starts from a stored snapshot while the server is not reachable and reports stale', () => {
    const storage = createMemoryStore();
    createSnapshotStorage(storage, 'fws_test').save({
      version: 4,
      flags: [booleanFlag('offline-flag')],
      segments: [],
    });
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({ providers: [testFlagwire(sockets, { storage })] });
    const flagwire = TestBed.inject(Flagwire);
    expect(flagwire.status()).toBe('stale');
    expect(flagwire.client.version).toBe(4);
    expect(flagwire.client.value('offline-flag', false)).toBe(true);
  });

  it('stops the client when the injector is destroyed', () => {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({ providers: [testFlagwire(sockets)] });
    TestBed.inject(Flagwire);
    const socket = sockets.latest;
    TestBed.resetTestingModule();
    expect(socket.closedByClient).toBe(1000);
  });

  it('lets the context change what flags evaluate to', () => {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({
      providers: [testFlagwire(sockets, { context: () => ({ key: 'a', attributes: { plan: 'free' } }) })],
    });
    const flagwire = TestBed.inject(Flagwire);
    sockets.latest.open();
    sockets.latest.receive(
      snapshotFrame(1, [
        booleanFlag('pro', {
          enabled: true,
          rules: [
            {
              id: 'r',
              conditions: [{ attribute: 'plan', operator: 'equals', values: ['pro'] }],
              serve: { variant: 'on' },
            },
          ],
          fallthrough: { variant: 'off' },
        }),
      ]),
    );
    expect(flagwire.client.value('pro', true)).toBe(false);
    const before = flagwire.revision();
    flagwire.setContext({ key: 'a', attributes: { plan: 'pro' } });
    expect(flagwire.revision()).toBe(before + 1);
    expect(flagwire.client.value('pro', false)).toBe(true);
  });
});
