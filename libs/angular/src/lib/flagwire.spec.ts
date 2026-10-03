import { ApplicationInitStatus, PLATFORM_ID, TransferState, makeStateKey } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { FlagSnapshot } from '@flagwire/core';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { booleanFlag } from '../../../core/test-support/fixtures';
import { FakeSockets, testFlagwire } from '../../test-support/flagwire-testing';
import { deriveSnapshotUrl } from './config';
import { Flagwire } from './flagwire';

const transferredSnapshot: FlagSnapshot = {
  version: 7,
  flags: [booleanFlag('banner')],
  segments: [],
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('Flagwire in the browser', () => {
  it('starts from the snapshot the server put into TransferState and still opens the stream', async () => {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: TransferState,
          useFactory: () => {
            const state = new TransferState();
            state.set(makeStateKey<FlagSnapshot>('flagwire:snapshot'), transferredSnapshot);
            return state;
          },
        },
        testFlagwire(sockets),
      ],
    });
    const flagwire = TestBed.inject(Flagwire);
    await TestBed.inject(ApplicationInitStatus).donePromise;
    expect(flagwire.client.version).toBe(7);
    expect(flagwire.client.value('banner', false)).toBe(true);
    expect(sockets.sockets).toHaveLength(1);
  });

  it('evaluates for the context returned by a context function', () => {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({
      providers: [testFlagwire(sockets, { context: () => ({ key: 'from-function', attributes: {} }) })],
    });
    expect(TestBed.inject(Flagwire).client.context.key).toBe('from-function');
  });

  it('changes the evaluation context through setContext', () => {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({ providers: [testFlagwire(sockets)] });
    const flagwire = TestBed.inject(Flagwire);
    flagwire.setContext({ key: 'someone-else', attributes: {} });
    expect(flagwire.client.context.key).toBe('someone-else');
  });
});

describe('Flagwire on the server', () => {
  function serverSetup(snapshotUrl?: string): { sockets: FakeSockets; flagwire: Flagwire } {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'server' },
        testFlagwire(sockets, snapshotUrl === undefined ? {} : { snapshotUrl }),
      ],
    });
    return { sockets, flagwire: TestBed.inject(Flagwire) };
  }

  function stubFetch(status: number): string[] {
    const urls: string[] = [];
    vi.stubGlobal('fetch', (url: string) => {
      urls.push(url);
      return Promise.resolve({
        ok: status === 200,
        status,
        text: () =>
          Promise.resolve(
            JSON.stringify({ v: 7, committedAtMs: 1, flags: transferredSnapshot.flags, segments: [] }),
          ),
      });
    });
    return urls;
  }

  it('fetches the snapshot from the url derived from the stream url and never opens a socket', async () => {
    const urls = stubFetch(200);
    const { sockets, flagwire } = serverSetup();
    await TestBed.inject(ApplicationInitStatus).donePromise;
    expect(urls).toEqual(['http://test/sdk/v1/snapshot']);
    expect(flagwire.client.value('banner', false)).toBe(true);
    expect(sockets.sockets).toHaveLength(0);
    expect(
      TestBed.inject(TransferState).get(makeStateKey<FlagSnapshot>('flagwire:snapshot'), null)?.version,
    ).toBe(7);
  });

  it('prefers an explicit snapshot url', async () => {
    const urls = stubFetch(200);
    serverSetup('http://internal:9000');
    await TestBed.inject(ApplicationInitStatus).donePromise;
    expect(urls).toEqual(['http://internal:9000/sdk/v1/snapshot']);
  });

  it('leaves the client empty when the api cannot be reached', async () => {
    stubFetch(503);
    const { flagwire } = serverSetup();
    await expect(TestBed.inject(ApplicationInitStatus).donePromise).resolves.toBeUndefined();
    expect(flagwire.client.value('banner', false)).toBe(false);
    expect(
      TestBed.inject(TransferState).get(makeStateKey<FlagSnapshot>('flagwire:snapshot'), null),
    ).toBeNull();
  });
});

describe('deriveSnapshotUrl', () => {
  it('turns the stream url into the http base url', () => {
    expect(deriveSnapshotUrl('wss://flags.example.com/sdk/v1/stream')).toBe('https://flags.example.com');
    expect(deriveSnapshotUrl('ws://localhost:8080/sdk/v1/stream/')).toBe('http://localhost:8080');
  });
});
