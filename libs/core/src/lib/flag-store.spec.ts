import { describe, expect, it } from 'vitest';
import { booleanFlag, deltasFrame, segment, snapshotFrame, upsertFlag } from '../../test-support/fixtures.js';
import { FlagStore } from './flag-store.js';

describe('FlagStore', () => {
  it('starts empty and without a version', () => {
    const store = new FlagStore();
    expect(store.version).toBeNull();
    expect(store.hasData).toBe(false);
    expect(store.snapshot()).toBeNull();
    expect(store.flag('a')).toBeUndefined();
  });

  it('replaces everything when a snapshot arrives', () => {
    const store = new FlagStore();
    store.applySnapshot(snapshotFrame(3, [booleanFlag('a'), booleanFlag('b')], [segment('beta')]));
    store.applySnapshot(snapshotFrame(7, [booleanFlag('c')]));
    expect(store.version).toBe(7);
    expect(store.flagKeys()).toEqual(['c']);
    expect(store.segments.size).toBe(0);
  });

  it('applies upserts and removals in order', () => {
    const store = new FlagStore();
    store.applySnapshot(snapshotFrame(1, [booleanFlag('a')]));
    const outcome = store.applyDeltas(
      deltasFrame(
        1,
        [upsertFlag(booleanFlag('b'))],
        [
          { op: 'remove', kind: 'flag', key: 'a' },
          { op: 'upsert', kind: 'segment', key: 'beta', config: segment('beta', ['u1']) },
        ],
      ),
    );
    expect(outcome).toBe('applied');
    expect(store.version).toBe(3);
    expect(store.flagKeys()).toEqual(['b']);
    expect(store.segments.get('beta')?.included).toEqual(['u1']);
  });

  it('removes a segment', () => {
    const store = new FlagStore();
    store.applySnapshot(snapshotFrame(1, [], [segment('beta')]));
    store.applyDeltas(deltasFrame(1, [{ op: 'remove', kind: 'segment', key: 'beta' }]));
    expect(store.segments.has('beta')).toBe(false);
  });

  it('reports a gap and changes nothing when the frame does not continue from the held version', () => {
    const store = new FlagStore();
    store.applySnapshot(snapshotFrame(5, [booleanFlag('a')]));
    expect(store.applyDeltas(deltasFrame(7, [upsertFlag(booleanFlag('b'))]))).toBe('gap');
    expect(store.version).toBe(5);
    expect(store.flagKeys()).toEqual(['a']);
  });

  it('reports a gap before any snapshot', () => {
    expect(new FlagStore().applyDeltas(deltasFrame(0, [upsertFlag(booleanFlag('a'))]))).toBe('gap');
  });

  it('reports a gap for entries that skip a version and applies none of them', () => {
    const store = new FlagStore();
    store.applySnapshot(snapshotFrame(1, [booleanFlag('a')]));
    const skipping = {
      t: 'deltas' as const,
      from: 1,
      to: 3,
      entries: [
        { v: 2, committedAtMs: 1, changes: [upsertFlag(booleanFlag('b'))] },
        { v: 9, committedAtMs: 2, changes: [upsertFlag(booleanFlag('c'))] },
      ],
    };
    expect(store.applyDeltas(skipping)).toBe('gap');
    expect(store.flagKeys()).toEqual(['a']);
  });

  it('reports a gap when the frame claims a different end version than its entries reach', () => {
    const store = new FlagStore();
    store.applySnapshot(snapshotFrame(1));
    expect(store.applyDeltas({ ...deltasFrame(1, [upsertFlag(booleanFlag('a'))]), to: 4 })).toBe('gap');
  });

  it('leaves the state alone for a deltas frame without entries', () => {
    const store = new FlagStore();
    store.applySnapshot(snapshotFrame(4, [booleanFlag('a')]));
    const seen: number[] = [];
    store.changes$.subscribe((version) => seen.push(version));
    expect(store.applyDeltas(deltasFrame(4))).toBe('unchanged');
    expect(seen).toEqual([]);
  });

  it('emits the new version after every applied frame', () => {
    const store = new FlagStore();
    const seen: number[] = [];
    store.changes$.subscribe((version) => seen.push(version));
    store.applySnapshot(snapshotFrame(2));
    store.applyDeltas(deltasFrame(2, [upsertFlag(booleanFlag('a'))], []));
    expect(seen).toEqual([2, 4]);
  });

  it('rebuilds the segment index after a segment changes', () => {
    const store = new FlagStore();
    store.applySnapshot(snapshotFrame(1, [], [segment('beta', ['u1'])]));
    const before = store.segments;
    store.applyDeltas(
      deltasFrame(1, [{ op: 'upsert', kind: 'segment', key: 'beta', config: segment('beta', ['u2']) }]),
    );
    expect(store.segments).not.toBe(before);
    expect(store.segments.get('beta')?.included).toEqual(['u2']);
  });

  it('starts from a given snapshot and hydrates from another', () => {
    const store = new FlagStore({ version: 2, flags: [booleanFlag('a')], segments: [] });
    expect(store.version).toBe(2);
    store.hydrate({ version: 9, flags: [booleanFlag('z')], segments: [] });
    expect(store.version).toBe(9);
    expect(store.snapshot()?.flags.map((flag) => flag.key)).toEqual(['z']);
  });
});
