import { assertInInjectionContext, computed, inject } from '@angular/core';
import type { Signal } from '@angular/core';
import { jsonEquals } from '@flagtide/core';
import type { JsonValue } from '@flagtide/core';
import { Flagtide } from './flagtide';

/**
 * A signal with the value of a flag. It is evaluated locally and updates when the server pushes a change, when
 * an override is set and when the context changes. The fallback is returned while the flag is unknown and when
 * its type differs from the type of the fallback.
 *
 * Call it in an injection context, for example a field initializer of a component.
 *
 * ```ts
 * readonly showBanner = injectFlag('header-banner', false);
 * ```
 */
export function injectFlag(key: string, fallback: boolean): Signal<boolean>;
export function injectFlag(key: string, fallback: string): Signal<string>;
export function injectFlag(key: string, fallback: number): Signal<number>;
export function injectFlag<T extends JsonValue>(key: string, fallback: T): Signal<T>;
export function injectFlag<T extends JsonValue>(key: string, fallback: T): Signal<T> {
  assertInInjectionContext(injectFlag);
  const flagtide = inject(Flagtide);
  return computed(
    () => {
      flagtide.revision();
      return flagtide.client.value(key, fallback);
    },
    { equal: jsonEquals },
  );
}
