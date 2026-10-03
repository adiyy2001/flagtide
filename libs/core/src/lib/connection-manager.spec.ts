import type { SchedulerLike } from 'rxjs';
import { TestScheduler } from 'rxjs/testing';
import { describe, expect, it } from 'vitest';
import { FakeEnvironment, FakeSockets, advance, throttled } from '../../test-support/fake-socket.js';
import { booleanFlag, deltasFrame, snapshotFrame, upsertFlag } from '../../test-support/fixtures.js';
import { ConnectionManager, backoffDelay } from './connection-manager.js';
import type { ConnectionManagerOptions, ConnectionStatus, StreamError } from './connection-manager.js';
import { FlagStore } from './flag-store.js';

interface Rig {
  readonly sockets: FakeSockets;
  readonly environment: FakeEnvironment;
  readonly store: FlagStore;
  readonly manager: ConnectionManager;
  readonly statuses: ConnectionStatus[];
  readonly errors: StreamError[];
}

type RigOptions = Partial<Omit<ConnectionManagerOptions, 'scheduler'>> & {
  stored?: boolean;
  throttle?: number;
};

const QUIET_TIMING = { connectTimeoutMs: 1e9, staleAfterMs: 1e9, offlineAfterMs: 1e9 };

function createRig(scheduler: TestScheduler, options: RigOptions): Rig {
  const sockets = new FakeSockets(() => scheduler.now());
  const environment = new FakeEnvironment();
  const store = new FlagStore(
    options.stored ? { version: 5, flags: [booleanFlag('a')], segments: [] } : undefined,
  );
  const effective: SchedulerLike =
    options.throttle === undefined ? scheduler : throttled(scheduler, options.throttle);
  const manager = new ConnectionManager({
    url: 'ws://test/sdk/v1/stream',
    sdkKey: 'fws_test',
    store,
    clientId: 'client-1',
    sdk: 'test/1',
    socketFactory: sockets.factory,
    environment,
    random: () => 0.5,
    ...options,
    scheduler: effective,
    timing: { ...QUIET_TIMING, ...options.timing },
  });
  const statuses: ConnectionStatus[] = [];
  const errors: StreamError[] = [];
  manager.status$.subscribe((status) => statuses.push(status));
  manager.errors$.subscribe((error) => errors.push(error));
  return { sockets, environment, store, manager, statuses, errors };
}

function withRig(options: RigOptions, body: (r: Rig, scheduler: TestScheduler) => void): void {
  const scheduler = new TestScheduler((actual, expected) => expect(actual).toEqual(expected));
  scheduler.run(() => {
    const r = createRig(scheduler, options);
    try {
      body(r, scheduler);
    } finally {
      r.manager.stop();
    }
  });
}

function goLive(r: Rig, version = 1): void {
  r.manager.start();
  r.sockets.latest.open();
  r.sockets.latest.receive(snapshotFrame(version));
}

function closeAndWaitForNextSocket(r: Rig, scheduler: TestScheduler): number {
  const before = r.sockets.sockets.length;
  const closedAt = scheduler.now();
  r.sockets.latest.closeFromServer(1006);
  while (r.sockets.sockets.length === before) {
    advance(scheduler, 1);
  }
  return r.sockets.latest.openedAt - closedAt;
}

describe('ConnectionManager handshake', () => {
  it('sends hello without a version when the store is empty, goes live on the snapshot and acknowledges it', () => {
    withRig({}, (r) => {
      r.manager.start();
      expect(r.manager.status).toBe('connecting');
      r.sockets.latest.open();
      expect(r.sockets.latest.sent).toEqual([
        { t: 'hello', sdkKey: 'fws_test', clientId: 'client-1', sdk: 'test/1' },
      ]);
      r.sockets.latest.receive(snapshotFrame(3, [booleanFlag('a')]));
      expect(r.manager.status).toBe('live');
      expect(r.store.version).toBe(3);
      expect(r.sockets.latest.sent.at(-1)).toEqual({ t: 'ack', v: 3 });
    });
  });

  it('starts as stale when a stored snapshot exists and resumes from its version', () => {
    withRig({ stored: true }, (r) => {
      expect(r.manager.status).toBe('stale');
      r.manager.start();
      r.sockets.latest.open();
      expect(r.sockets.latest.sent[0]).toMatchObject({ t: 'hello', version: 5 });
      r.sockets.latest.receive(deltasFrame(5, [upsertFlag(booleanFlag('b'))]));
      expect(r.manager.status).toBe('live');
      expect(r.sockets.latest.sent.at(-1)).toEqual({ t: 'ack', v: 6 });
      expect(r.store.flagKeys()).toEqual(['a', 'b']);
    });
  });

  it('goes live without acknowledging when the deltas frame is empty', () => {
    withRig({ stored: true }, (r) => {
      r.manager.start();
      r.sockets.latest.open();
      r.sockets.latest.receive(deltasFrame(5));
      expect(r.manager.status).toBe('live');
      expect(r.sockets.latest.sent).toHaveLength(1);
    });
  });

  it('acknowledges every applied frame after applying it', () => {
    withRig({}, (r) => {
      goLive(r);
      r.sockets.latest.receive(deltasFrame(1, [upsertFlag(booleanFlag('x'))]));
      expect(r.sockets.latest.sent.map((frame) => frame['t'])).toEqual(['hello', 'ack', 'ack']);
      expect(r.sockets.latest.sent.at(-1)).toEqual({ t: 'ack', v: 2 });
    });
  });

  it('ignores a second start', () => {
    withRig({}, (r) => {
      r.manager.start();
      r.manager.start();
      expect(r.sockets.sockets).toHaveLength(1);
    });
  });
});

describe('ConnectionManager backoff', () => {
  it('computes full jitter delays within the capped exponential window', () => {
    expect(backoffDelay(0, () => 0, 500, 30_000)).toBe(0);
    expect(backoffDelay(0, () => 0.999999, 500, 30_000)).toBe(499);
    expect(backoffDelay(3, () => 0.999999, 500, 30_000)).toBe(3999);
    expect(backoffDelay(10, () => 0.999999, 500, 30_000)).toBe(29_999);
    expect(backoffDelay(5000, () => 0.5, 500, 30_000)).toBe(15_000);
  });

  it('grows the delay between attempts and stops growing at the cap', () => {
    withRig({ random: () => 0.999 }, (r, scheduler) => {
      r.manager.start();
      const gaps = Array.from({ length: 9 }, () => closeAndWaitForNextSocket(r, scheduler));
      expect(gaps).toEqual([499, 999, 1998, 3996, 7992, 15_984, 29_970, 29_970, 29_970]);
    });
  });

  it('draws every delay from the random source', () => {
    withRig({ random: () => 0 }, (r, scheduler) => {
      r.manager.start();
      expect(closeAndWaitForNextSocket(r, scheduler)).toBe(0);
    });
  });

  it('resets the backoff once a frame arrived on the new connection', () => {
    withRig({ random: () => 0.999 }, (r, scheduler) => {
      r.manager.start();
      closeAndWaitForNextSocket(r, scheduler);
      closeAndWaitForNextSocket(r, scheduler);
      closeAndWaitForNextSocket(r, scheduler);
      r.sockets.latest.open();
      r.sockets.latest.receive(snapshotFrame(1));
      expect(closeAndWaitForNextSocket(r, scheduler)).toBe(499);
    });
  });

  it('does not reset the backoff when the socket opens but nothing arrives', () => {
    withRig({ random: () => 0.999 }, (r, scheduler) => {
      r.manager.start();
      r.sockets.latest.open();
      closeAndWaitForNextSocket(r, scheduler);
      r.sockets.latest.open();
      expect(closeAndWaitForNextSocket(r, scheduler)).toBe(999);
    });
  });

  it('retries when the socket cannot be created', () => {
    const scheduler = new TestScheduler((actual, expected) => expect(actual).toEqual(expected));
    scheduler.run(() => {
      let failures = 2;
      const sockets = new FakeSockets(() => scheduler.now());
      const manager = new ConnectionManager({
        url: 'ws://test',
        sdkKey: 'k',
        store: new FlagStore(),
        scheduler,
        random: () => 0,
        timing: QUIET_TIMING,
        socketFactory: (url, handlers) => {
          if (failures-- > 0) {
            throw new Error('refused');
          }
          return sockets.factory(url, handlers);
        },
      });
      manager.start();
      advance(scheduler, 10);
      expect(sockets.sockets).toHaveLength(1);
      manager.stop();
    });
  });
});

describe('ConnectionManager status', () => {
  it('walks connecting, live, stale, live over a drop and a reconnect', () => {
    const scheduler = new TestScheduler((actual, expected) => expect(actual).toEqual(expected));
    scheduler.run(({ expectObservable }) => {
      const r = createRig(scheduler, { random: () => 0 });
      expectObservable(r.manager.status$).toBe('a 9ms b 9ms c 9ms d', {
        a: 'connecting',
        b: 'live',
        c: 'stale',
        d: 'live',
      });
      scheduler.schedule(() => r.manager.start(), 0);
      scheduler.schedule(() => {
        r.sockets.latest.open();
        r.sockets.latest.receive(snapshotFrame(1));
      }, 10);
      scheduler.schedule(() => r.sockets.latest.closeFromServer(1006), 20);
      scheduler.schedule(() => r.sockets.latest.receive(snapshotFrame(2)), 30);
      scheduler.schedule(() => r.manager.stop(), 40);
    });
  });

  it('keeps reporting connecting while the first attempts fail', () => {
    withRig({}, (r, scheduler) => {
      r.manager.start();
      closeAndWaitForNextSocket(r, scheduler);
      closeAndWaitForNextSocket(r, scheduler);
      expect(r.statuses).toEqual(['connecting']);
    });
  });

  it('turns offline when the server stays unreachable beyond the limit, and live again when it answers', () => {
    withRig({ stored: true, random: () => 0.999, timing: { offlineAfterMs: 20_000 } }, (r, scheduler) => {
      r.manager.start();
      for (let attempt = 0; attempt < 7; attempt++) {
        closeAndWaitForNextSocket(r, scheduler);
      }
      expect(r.manager.status).toBe('offline');
      r.sockets.latest.open();
      r.sockets.latest.receive(snapshotFrame(9));
      expect(r.manager.status).toBe('live');
    });
  });
});

describe('ConnectionManager heartbeat watchdog', () => {
  it('goes stale and reconnects when nothing arrives for the stale limit', () => {
    withRig({ random: () => 0, timing: { staleAfterMs: 40_000 } }, (r, scheduler) => {
      goLive(r);
      const first = r.sockets.latest;
      advance(scheduler, 39_999);
      expect(r.manager.status).toBe('live');
      expect(first.closedByClient).toBeNull();
      advance(scheduler, 1);
      expect(first.closedByClient).toBe(1000);
      expect(r.statuses).toEqual(['connecting', 'live', 'stale']);
      expect(r.sockets.sockets).toHaveLength(2);
    });
  });

  it('stays live while heartbeats keep arriving and notices when they stop', () => {
    withRig({ timing: { staleAfterMs: 40_000 } }, (r, scheduler) => {
      goLive(r);
      for (let beat = 1; beat <= 6; beat++) {
        advance(scheduler, 15_000);
        r.sockets.latest.receive({ t: 'hb', ts: beat, v: 1 });
      }
      expect(r.sockets.sockets).toHaveLength(1);
      expect(r.manager.status).toBe('live');
      advance(scheduler, 40_000);
      expect(r.manager.status).toBe('stale');
    });
  });

  it('gives up on a connection that never answers after the connect timeout', () => {
    withRig({ random: () => 0, timing: { connectTimeoutMs: 5000 } }, (r, scheduler) => {
      r.manager.start();
      advance(scheduler, 5001);
      expect(r.sockets.sockets).toHaveLength(2);
      expect(r.sockets.sockets[0]?.closedByClient).toBe(1000);
    });
  });

  it('checks timestamps when the tab becomes visible, even though its timers were throttled', () => {
    withRig({ throttle: 100, random: () => 0, timing: { staleAfterMs: 40_000 } }, (r, scheduler) => {
      goLive(r);
      advance(scheduler, 41_000);
      expect(r.sockets.sockets).toHaveLength(1);
      r.environment.show();
      expect(r.sockets.sockets[0]?.closedByClient).toBe(1000);
      expect(r.manager.status).toBe('stale');
    });
  });

  it('does nothing on visibility while the connection is fresh and keeps watching', () => {
    withRig({ timing: { staleAfterMs: 40_000 } }, (r, scheduler) => {
      goLive(r);
      advance(scheduler, 10_000);
      r.environment.show();
      expect(r.sockets.sockets).toHaveLength(1);
      advance(scheduler, 30_001);
      expect(r.sockets.sockets[0]?.closedByClient).toBe(1000);
    });
  });
});

describe('ConnectionManager network changes', () => {
  it('goes offline when the browser does, stops trying and reconnects at once when it is back', () => {
    withRig({ random: () => 0.999 }, (r, scheduler) => {
      goLive(r);
      const first = r.sockets.latest;
      r.environment.goOffline();
      expect(r.manager.status).toBe('offline');
      expect(first.closedByClient).toBe(1000);
      advance(scheduler, 120_000);
      expect(r.sockets.sockets).toHaveLength(1);
      r.environment.goOnline();
      expect(r.manager.status).toBe('stale');
      expect(r.sockets.sockets).toHaveLength(2);
      expect(r.sockets.latest.openedAt).toBe(120_000);
    });
  });

  it('does not connect when it starts offline and connects when the network arrives', () => {
    withRig({}, (r) => {
      r.environment.online = false;
      r.manager.start();
      expect(r.manager.status).toBe('offline');
      expect(r.sockets.sockets).toHaveLength(0);
      r.environment.goOnline();
      expect(r.sockets.sockets).toHaveLength(1);
      expect(r.manager.status).toBe('connecting');
    });
  });

  it('reports offline instead of retrying when a connection drops while the browser is offline', () => {
    withRig({}, (r, scheduler) => {
      r.manager.start();
      r.environment.online = false;
      r.sockets.latest.closeFromServer(1006);
      advance(scheduler, 60_000);
      expect(r.manager.status).toBe('offline');
      expect(r.sockets.sockets).toHaveLength(1);
    });
  });
});

describe('ConnectionManager failures', () => {
  it('stops for good on an unauthorized close and reports it', () => {
    withRig({}, (r, scheduler) => {
      r.manager.start();
      r.sockets.latest.closeFromServer(4401);
      advance(scheduler, 600_000);
      expect(r.manager.status).toBe('offline');
      expect(r.sockets.sockets).toHaveLength(1);
      expect(r.errors).toEqual([{ code: 4401, message: expect.any(String) }]);
      r.environment.goOnline();
      expect(r.sockets.sockets).toHaveLength(1);
    });
  });

  it('forwards an error frame', () => {
    withRig({}, (r) => {
      r.manager.start();
      r.sockets.latest.receive({ t: 'error', code: 4429, message: 'slow' });
      expect(r.errors).toEqual([{ code: 4429, message: 'slow' }]);
    });
  });

  it('drops a connection that sends something it cannot read and reconnects', () => {
    withRig({ random: () => 0 }, (r, scheduler) => {
      r.manager.start();
      r.sockets.latest.open();
      r.sockets.latest.receiveText('{"t":"party"}');
      expect(r.errors[0]?.code).toBe(4400);
      advance(scheduler, 10);
      expect(r.sockets.sockets).toHaveLength(2);
      expect(r.sockets.sockets[0]?.closedByClient).toBe(1000);
    });
  });

  it('asks for a snapshot after deltas that do not continue from its version, then resumes by version', () => {
    withRig({ stored: true }, (r, scheduler) => {
      r.manager.start();
      r.sockets.latest.open();
      r.sockets.latest.receive(deltasFrame(8, [upsertFlag(booleanFlag('x'))]));
      expect(r.store.version).toBe(5);
      expect(r.sockets.sockets[0]?.closedByClient).toBe(1000);
      advance(scheduler, 1);
      expect(r.sockets.sockets).toHaveLength(2);
      r.sockets.latest.open();
      expect(r.sockets.latest.sent[0]).not.toHaveProperty('version');
      r.sockets.latest.receive(snapshotFrame(12));
      closeAndWaitForNextSocket(r, scheduler);
      r.sockets.latest.open();
      expect(r.sockets.latest.sent[0]).toMatchObject({ version: 12 });
    });
  });

  it('ignores events from a socket it has already dropped', () => {
    withRig({}, (r) => {
      goLive(r);
      const old = r.sockets.latest;
      r.environment.goOffline();
      old.receive(snapshotFrame(99));
      old.closeFromServer(1006);
      expect(r.store.version).toBe(1);
      expect(r.manager.status).toBe('offline');
    });
  });

  it('stops reconnecting after stop and can be started again', () => {
    withRig({ random: () => 0 }, (r, scheduler) => {
      r.manager.start();
      r.sockets.latest.closeFromServer(1006);
      r.manager.stop();
      advance(scheduler, 1000);
      expect(r.sockets.sockets).toHaveLength(1);
      r.manager.start();
      expect(r.sockets.sockets).toHaveLength(2);
    });
  });
});
