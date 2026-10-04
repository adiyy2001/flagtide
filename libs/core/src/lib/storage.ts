import { murmur3x86_32OfText } from './murmur3.js';
import type { FlagSnapshot } from './flag-store.js';

/** The smallest key value store the SDK needs. Implementations must not throw. */
export interface KeyValueStore {
  get(key: string): string | null;
  set(key: string, value: string): void;
  remove(key: string): void;
}

/** Keeps the last snapshot so that a page can start while the server is unreachable. */
export interface SnapshotStorage {
  load(): FlagSnapshot | null;
  save(snapshot: FlagSnapshot): void;
  clear(): void;
}

interface WebStorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

/** A store that lives as long as the object. It is the fallback when browser storage is unavailable. */
export function createMemoryStore(): KeyValueStore {
  const values = new Map<string, string>();
  return {
    get: (key) => values.get(key) ?? null,
    set: (key, value) => {
      values.set(key, value);
    },
    remove: (key) => {
      values.delete(key);
    },
  };
}

function attempt<T>(action: () => T, fallback: T): T {
  try {
    return action();
  } catch {
    return fallback;
  }
}

/**
 * Wraps a Web Storage object. The accessor and every call are guarded, because storage throws in private
 * windows, when site data is blocked and when quota is exceeded. Pass a function so the accessor itself
 * is evaluated inside the guard.
 */
export function createWebStore(provider: () => WebStorageLike | null | undefined): KeyValueStore {
  const storage = (): WebStorageLike | null => attempt(() => provider() ?? null, null);
  return {
    get: (key) => attempt(() => storage()?.getItem(key) ?? null, null),
    set: (key, value) => {
      attempt(() => storage()?.setItem(key, value), undefined);
    },
    remove: (key) => {
      attempt(() => storage()?.removeItem(key), undefined);
    },
  };
}

/** `localStorage` when the browser has it, a store that remembers nothing otherwise (servers, blocked storage). */
export function createLocalStore(): KeyValueStore {
  return createWebStore(() => (typeof localStorage === 'undefined' ? null : localStorage));
}

function isStoredSnapshot(value: unknown): value is FlagSnapshot {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate['version'] === 'number' &&
    Number.isSafeInteger(candidate['version']) &&
    Array.isArray(candidate['flags']) &&
    Array.isArray(candidate['segments'])
  );
}

/** The storage key for a snapshot. The SDK key is hashed so it does not sit in storage as plain text. */
export function snapshotStorageKey(sdkKey: string): string {
  return `flagtide:snapshot:${murmur3x86_32OfText(sdkKey).toString(16)}`;
}

/** Stores the snapshot of one SDK key as JSON. Corrupt or foreign content is dropped and reported as no snapshot. */
export function createSnapshotStorage(store: KeyValueStore, sdkKey: string): SnapshotStorage {
  const key = snapshotStorageKey(sdkKey);
  return {
    load: () => {
      const text = store.get(key);
      if (text === null) {
        return null;
      }
      const parsed = attempt<unknown>(() => JSON.parse(text), null);
      if (!isStoredSnapshot(parsed)) {
        store.remove(key);
        return null;
      }
      return { version: parsed.version, flags: parsed.flags, segments: parsed.segments };
    },
    save: (snapshot) => store.set(key, JSON.stringify(snapshot)),
    clear: () => store.remove(key),
  };
}
