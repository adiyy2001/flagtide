import { describe, expect, it } from 'vitest';
import { FlagOverrides } from './overrides.js';
import { createMemoryStore } from './storage.js';

describe('FlagOverrides', () => {
  it('sets, reads and clears values and announces each change', () => {
    const overrides = new FlagOverrides();
    let announced = 0;
    overrides.changes$.subscribe(() => (announced += 1));
    overrides.set('a', true);
    overrides.set('b', { x: 1 });
    expect(overrides.get('a')).toBe(true);
    expect([...overrides.entries().keys()]).toEqual(['a', 'b']);
    overrides.clear('a');
    overrides.clear('missing');
    overrides.clearAll();
    overrides.clearAll();
    expect(overrides.entries().size).toBe(0);
    expect(announced).toBe(4);
  });

  it('survives a reload through the store', () => {
    const store = createMemoryStore();
    new FlagOverrides(store).set('a', 'x');
    expect(new FlagOverrides(store).get('a')).toBe('x');
  });

  it('ignores stored content it cannot read', () => {
    const store = createMemoryStore();
    store.set('flagtide:overrides', '{broken');
    expect(new FlagOverrides(store).entries().size).toBe(0);
    store.set('flagtide:overrides', '[1]');
    expect(new FlagOverrides(store).entries().size).toBe(0);
  });
});
