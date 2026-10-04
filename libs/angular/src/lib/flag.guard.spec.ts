import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import type { CanMatchFn, PartialMatchRouteSnapshot } from '@angular/router';
import type { FlagConfig } from '@flagtide/core';
import { firstValueFrom, isObservable } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { booleanFlag, snapshotFrame, variantFlag } from '../../../core/test-support/fixtures';
import { FakeSockets, testFlagtide } from '../../test-support/flagtide-testing';
import { flagtideGuard } from './flag.guard';

async function run(guard: CanMatchFn): Promise<boolean | UrlTree> {
  const result = TestBed.runInInjectionContext(() =>
    guard({ path: 'x' }, [], {} as PartialMatchRouteSnapshot),
  );
  if (isObservable(result)) {
    return firstValueFrom(result) as Promise<boolean | UrlTree>;
  }
  return result as boolean | UrlTree;
}

describe('flagtideGuard', () => {
  let sockets: FakeSockets;

  beforeEach(() => {
    sockets = new FakeSockets();
    TestBed.configureTestingModule({ providers: [testFlagtide(sockets)] });
    TestBed.inject(Router);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function deliver(flags: readonly FlagConfig[]): void {
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(1, flags));
  }

  it('lets the route match while the flag is on', async () => {
    deliver([booleanFlag('beta')]);
    expect(await run(flagtideGuard('beta'))).toBe(true);
  });

  it('refuses the route while the flag is off', async () => {
    deliver([booleanFlag('beta', { enabled: false })]);
    expect(await run(flagtideGuard('beta'))).toBe(false);
  });

  it('redirects to the given url when the flag does not match', async () => {
    deliver([booleanFlag('beta', { enabled: false })]);
    const result = await run(flagtideGuard('beta', { redirectTo: '/home' }));
    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/home');
  });

  it('compares with the expected value for non boolean flags', async () => {
    deliver([variantFlag('layout', 'string', [{ key: 'a', value: 'grid' }], 'a')]);
    expect(await run(flagtideGuard('layout', { equals: 'grid' }))).toBe(true);
    expect(await run(flagtideGuard('layout', { equals: 'list' }))).toBe(false);
  });

  it('refuses an unknown flag', async () => {
    deliver([]);
    expect(await run(flagtideGuard('ghost'))).toBe(false);
  });

  it('waits for the first flags when the browser has none yet', async () => {
    sockets.latest.open();
    const pending = run(flagtideGuard('beta'));
    sockets.latest.receive(snapshotFrame(1, [booleanFlag('beta')]));
    expect(await pending).toBe(true);
  });

  it('gives up waiting after the configured time and decides with the fallback', async () => {
    vi.useFakeTimers();
    const pending = run(flagtideGuard('beta', { waitMs: 500 }));
    await vi.advanceTimersByTimeAsync(501);
    expect(await pending).toBe(false);
  });
});
