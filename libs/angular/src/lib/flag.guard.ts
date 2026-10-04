import { isPlatformServer } from '@angular/common';
import { PLATFORM_ID, inject } from '@angular/core';
import { Router } from '@angular/router';
import type { CanMatchFn, UrlTree } from '@angular/router';
import { jsonEquals } from '@flagtide/core';
import type { JsonValue } from '@flagtide/core';
import { map, of, race, take, timer } from 'rxjs';
import type { Observable } from 'rxjs';
import { Flagtide } from './flagtide';

/** Options of {@link flagtideGuard}. */
export interface FlagtideGuardOptions {
  /** The value that lets the route match. Defaults to `true`. */
  readonly equals?: JsonValue;
  /** Where to send the user when the flag does not match, as a URL. Without it the route simply does not match. */
  readonly redirectTo?: string;
  /**
   * How long to wait for the first flags when the browser has none yet, in milliseconds. Defaults to 2000. After
   * that the guard decides with the fallback, which never matches an unknown flag.
   */
  readonly waitMs?: number;
}

const DEFAULT_WAIT_MS = 2000;

/**
 * A `canMatch` guard that lets a route match only while a flag has the expected value. The flag is read when
 * the navigation happens.
 *
 * ```ts
 * { path: 'beta', canMatch: [flagtideGuard('beta-recommendations', { redirectTo: '/' })], loadComponent: ... }
 * ```
 */
export function flagtideGuard(key: string, options: FlagtideGuardOptions = {}): CanMatchFn {
  return (): Observable<boolean | UrlTree> => {
    const flagtide = inject(Flagtide);
    const router = inject(Router);
    const waits = !isPlatformServer(inject(PLATFORM_ID)) && flagtide.client.version === null;
    const ready: Observable<unknown> = waits
      ? race(flagtide.client.changes$.pipe(take(1)), timer(options.waitMs ?? DEFAULT_WAIT_MS))
      : of(true);
    const expected = options.equals ?? true;
    return ready.pipe(
      map(() => {
        const resolution = flagtide.client.resolve(key, expected);
        const known = resolution.reason !== 'FLAG_NOT_FOUND' && resolution.reason !== 'TYPE_MISMATCH';
        if (known && jsonEquals(resolution.value, expected)) {
          return true;
        }
        return options.redirectTo === undefined ? false : router.parseUrl(options.redirectTo);
      }),
    );
  };
}
