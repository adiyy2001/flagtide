import { TestBed } from '@angular/core/testing';
import { createMemoryStore } from '@flagtide/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { KeyStore } from './key-store';
import { RUNTIME_CONFIG } from './runtime-config';
import { LOCAL_STORE } from './tokens';

function setup(stored?: string) {
  const store = createMemoryStore();
  if (stored !== undefined) {
    store.set('flagtide.admin.keys', stored);
  }
  TestBed.configureTestingModule({
    providers: [
      {
        provide: RUNTIME_CONFIG,
        useValue: { apiUrl: 'http://x', project: 'demo', adminKeys: { dev: 'from-config' } },
      },
      { provide: LOCAL_STORE, useValue: store },
    ],
  });
  return { keys: TestBed.inject(KeyStore), store };
}

describe('KeyStore', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('serves keys from the runtime config', () => {
    const { keys } = setup();
    expect(keys.keyFor('dev')).toBe('from-config');
    expect(keys.keyFor('prod')).toBeNull();
    expect(keys.isRemembered('dev')).toBe(false);
  });

  it('lets a remembered key win over the config and persists it', () => {
    const { keys, store } = setup();
    keys.remember('dev', 'typed');
    expect(keys.keyFor('dev')).toBe('typed');
    expect(keys.isRemembered('dev')).toBe(true);
    expect(JSON.parse(store.get('flagtide.admin.keys') ?? '{}')).toEqual({ dev: 'typed' });
  });

  it('forgets a remembered key and falls back to the config', () => {
    const { keys } = setup();
    keys.remember('dev', 'typed');
    keys.forget('dev');
    expect(keys.keyFor('dev')).toBe('from-config');
  });

  it('prefers the asked environment, then any key', () => {
    const { keys } = setup();
    keys.remember('prod', 'p');
    expect(keys.firstKey('prod')).toBe('p');
    expect(keys.firstKey('staging')).toBe('from-config');
    expect(keys.firstKey(null)).toBe('from-config');
  });

  it.each(['not json', '[1]', '"text"', '{"a":1,"b":"ok"}'])('survives stored value %s', (stored) => {
    const { keys } = setup(stored);
    expect(keys.keyFor('b') === 'ok' || keys.keyFor('b') === null).toBe(true);
    expect(keys.keyFor('dev')).toBe('from-config');
  });
});
