import { InjectionToken } from '@angular/core';
import { createLocalStore } from '@flagwire/core';
import type { KeyValueStore } from '@flagwire/core';

export const LOCAL_STORE = new InjectionToken<KeyValueStore>('LOCAL_STORE', {
  providedIn: 'root',
  factory: () => createLocalStore(),
});

export const FETCH = new InjectionToken<typeof fetch>('FETCH', {
  providedIn: 'root',
  factory: () => (input, init) => globalThis.fetch(input, init),
});
