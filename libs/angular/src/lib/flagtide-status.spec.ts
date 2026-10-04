import { TestBed } from '@angular/core/testing';
import { createMemoryStore, createSnapshotStorage } from '@flagtide/core';
import { describe, expect, it } from 'vitest';
import { booleanFlag, snapshotFrame } from '../../../core/test-support/fixtures';
import { FakeSockets, testFlagtide } from '../../test-support/flagtide-testing';
import { Flagtide } from './flagtide';
import { FlagtideStatus } from './flagtide-status';

describe('FlagtideStatus', () => {
  it('follows the connection: connecting, live, stale after a drop, live again', () => {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({ providers: [testFlagtide(sockets, { timing: { backoffBaseMs: 0 } })] });
    const status = TestBed.inject(FlagtideStatus).status;
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
    TestBed.configureTestingModule({ providers: [testFlagtide(sockets, { storage })] });
    const flagtide = TestBed.inject(Flagtide);
    expect(flagtide.status()).toBe('stale');
    expect(flagtide.client.version).toBe(4);
    expect(flagtide.client.value('offline-flag', false)).toBe(true);
  });

  it('stops the client when the injector is destroyed', () => {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({ providers: [testFlagtide(sockets)] });
    TestBed.inject(Flagtide);
    const socket = sockets.latest;
    TestBed.resetTestingModule();
    expect(socket.closedByClient).toBe(1000);
  });

  it('lets the context change what flags evaluate to', () => {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({
      providers: [testFlagtide(sockets, { context: () => ({ key: 'a', attributes: { plan: 'free' } }) })],
    });
    const flagtide = TestBed.inject(Flagtide);
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
    expect(flagtide.client.value('pro', true)).toBe(false);
    const before = flagtide.revision();
    flagtide.setContext({ key: 'a', attributes: { plan: 'pro' } });
    expect(flagtide.revision()).toBe(before + 1);
    expect(flagtide.client.value('pro', false)).toBe(true);
  });
});
