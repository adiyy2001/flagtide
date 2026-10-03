import { describe, expect, it } from 'vitest';
import { createMemoryStore, createSnapshotStorage, createWebStore, snapshotStorageKey } from './storage.js';

const snapshot = { version: 4, flags: [], segments: [] };

describe('createWebStore', () => {
  it('reads and writes through the storage object', () => {
    const backing = new Map<string, string>();
    const store = createWebStore(() => ({
      getItem: (key) => backing.get(key) ?? null,
      setItem: (key, value) => void backing.set(key, value),
      removeItem: (key) => void backing.delete(key),
    }));
    store.set('k', 'v');
    expect(store.get('k')).toBe('v');
    store.remove('k');
    expect(store.get('k')).toBeNull();
  });

  it('survives an accessor that throws', () => {
    const store = createWebStore(() => {
      throw new DOMException('blocked', 'SecurityError');
    });
    expect(() => store.set('k', 'v')).not.toThrow();
    expect(store.get('k')).toBeNull();
    expect(() => store.remove('k')).not.toThrow();
  });

  it('survives calls that throw, such as a full quota', () => {
    const store = createWebStore(() => ({
      getItem: () => {
        throw new Error('broken');
      },
      setItem: () => {
        throw new DOMException('full', 'QuotaExceededError');
      },
      removeItem: () => {
        throw new Error('broken');
      },
    }));
    expect(() => store.set('k', 'v')).not.toThrow();
    expect(store.get('k')).toBeNull();
    expect(() => store.remove('k')).not.toThrow();
  });

  it('behaves as empty when there is no storage', () => {
    const store = createWebStore(() => undefined);
    store.set('k', 'v');
    expect(store.get('k')).toBeNull();
  });
});

describe('createSnapshotStorage', () => {
  it('round trips a snapshot', () => {
    const storage = createSnapshotStorage(createMemoryStore(), 'fws_key');
    expect(storage.load()).toBeNull();
    storage.save(snapshot);
    expect(storage.load()).toEqual(snapshot);
    storage.clear();
    expect(storage.load()).toBeNull();
  });

  it('keeps snapshots of different SDK keys apart', () => {
    const store = createMemoryStore();
    createSnapshotStorage(store, 'fws_one').save(snapshot);
    expect(createSnapshotStorage(store, 'fws_two').load()).toBeNull();
  });

  it('does not put the SDK key into the storage key', () => {
    expect(snapshotStorageKey('fws_secret_value')).not.toContain('secret');
  });

  it.each([
    ['not json', '{broken'],
    ['a wrong shape', '{"version":"x"}'],
    ['an array', '[]'],
  ])('drops %s and reports no snapshot', (_name, content) => {
    const store = createMemoryStore();
    store.set(snapshotStorageKey('k'), content);
    const storage = createSnapshotStorage(store, 'k');
    expect(storage.load()).toBeNull();
    expect(store.get(snapshotStorageKey('k'))).toBeNull();
  });
});
