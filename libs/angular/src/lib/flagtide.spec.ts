import { ApplicationInitStatus, PLATFORM_ID, TransferState, makeStateKey } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { FlagSnapshot } from '@flagtide/core';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { booleanFlag } from '../../../core/test-support/fixtures';
import { FakeSockets, testFlagtide } from '../../test-support/flagtide-testing';
import { deriveSnapshotUrl } from './config';
import { Flagtide } from './flagtide';

const transferredSnapshot: FlagSnapshot = {
  version: 7,
  flags: [booleanFlag('banner')],
  segments: [],
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('Flagtide in the browser', () => {
  it('starts from the snapshot the server put into TransferState and still opens the stream', async () => {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: TransferState,
          useFactory: () => {
            const state = new TransferState();
            state.set(makeStateKey<FlagSnapshot>('flagtide:snapshot'), transferredSnapshot);
            return state;
          },
        },
        testFlagtide(sockets),
      ],
    });
    const flagtide = TestBed.inject(Flagtide);
    await TestBed.inject(ApplicationInitStatus).donePromise;
    expect(flagtide.client.version).toBe(7);
    expect(flagtide.client.value('banner', false)).toBe(true);
    expect(sockets.sockets).toHaveLength(1);
  });

  it('evaluates for the context returned by a context function', () => {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({
      providers: [testFlagtide(sockets, { context: () => ({ key: 'from-function', attributes: {} }) })],
    });
    expect(TestBed.inject(Flagtide).client.context.key).toBe('from-function');
  });

  it('changes the evaluation context through setContext', () => {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({ providers: [testFlagtide(sockets)] });
    const flagtide = TestBed.inject(Flagtide);
    flagtide.setContext({ key: 'someone-else', attributes: {} });
    expect(flagtide.client.context.key).toBe('someone-else');
  });
});

describe('Flagtide on the server', () => {
  function serverSetup(snapshotUrl?: string): { sockets: FakeSockets; flagtide: Flagtide } {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'server' },
        testFlagtide(sockets, snapshotUrl === undefined ? {} : { snapshotUrl }),
      ],
    });
    return { sockets, flagtide: TestBed.inject(Flagtide) };
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
    const { sockets, flagtide } = serverSetup();
    await TestBed.inject(ApplicationInitStatus).donePromise;
    expect(urls).toEqual(['http://test/sdk/v1/snapshot']);
    expect(flagtide.client.value('banner', false)).toBe(true);
    expect(sockets.sockets).toHaveLength(0);
    expect(
      TestBed.inject(TransferState).get(makeStateKey<FlagSnapshot>('flagtide:snapshot'), null)?.version,
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
    const { flagtide } = serverSetup();
    await expect(TestBed.inject(ApplicationInitStatus).donePromise).resolves.toBeUndefined();
    expect(flagtide.client.value('banner', false)).toBe(false);
    expect(
      TestBed.inject(TransferState).get(makeStateKey<FlagSnapshot>('flagtide:snapshot'), null),
    ).toBeNull();
  });
});

describe('deriveSnapshotUrl', () => {
  it('turns the stream url into the http base url', () => {
    expect(deriveSnapshotUrl('wss://flags.example.com/sdk/v1/stream')).toBe('https://flags.example.com');
    expect(deriveSnapshotUrl('ws://localhost:8080/sdk/v1/stream/')).toBe('http://localhost:8080');
  });
});
