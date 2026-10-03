import { describe, expect, it } from 'vitest';
import { parseServerFrame, parseSnapshotBody } from './protocol.js';

const flag = { key: 'a' };

describe('parseServerFrame', () => {
  it('parses a snapshot', () => {
    const frame = parseServerFrame(JSON.stringify({ t: 'snapshot', v: 3, flags: [flag], segments: [] }));
    expect(frame?.t).toBe('snapshot');
  });

  it('parses deltas with upserts and removals', () => {
    const text = JSON.stringify({
      t: 'deltas',
      from: 1,
      to: 2,
      entries: [
        {
          v: 2,
          committedAtMs: 5,
          changes: [
            { op: 'upsert', kind: 'flag', key: 'a', config: flag },
            { op: 'remove', kind: 'segment', key: 'beta' },
          ],
        },
      ],
    });
    expect(parseServerFrame(text)?.t).toBe('deltas');
  });

  it('parses a heartbeat and an error', () => {
    expect(parseServerFrame('{"t":"hb","ts":1,"v":4}')?.t).toBe('hb');
    expect(parseServerFrame('{"t":"error","code":4401,"message":"no"}')?.t).toBe('error');
  });

  it.each([
    ['not json', 'nope'],
    ['an array', '[]'],
    ['a number', '3'],
    ['no type', '{"v":1}'],
    ['an unknown type', '{"t":"party"}'],
    ['a snapshot without flags', '{"t":"snapshot","v":1,"segments":[]}'],
    ['a snapshot with a negative version', '{"t":"snapshot","v":-1,"flags":[],"segments":[]}'],
    ['a snapshot with a flag without key', '{"t":"snapshot","v":1,"flags":[{}],"segments":[]}'],
    ['deltas without entries', '{"t":"deltas","from":1,"to":2}'],
    [
      'deltas with an unknown op',
      '{"t":"deltas","from":1,"to":2,"entries":[{"v":2,"changes":[{"op":"x","kind":"flag","key":"a"}]}]}',
    ],
    [
      'deltas with an unknown kind',
      '{"t":"deltas","from":1,"to":2,"entries":[{"v":2,"changes":[{"op":"remove","kind":"x","key":"a"}]}]}',
    ],
    [
      'an upsert without config',
      '{"t":"deltas","from":1,"to":2,"entries":[{"v":2,"changes":[{"op":"upsert","kind":"flag","key":"a"}]}]}',
    ],
    ['a heartbeat without time', '{"t":"hb"}'],
    ['an error without code', '{"t":"error","message":"x"}'],
  ])('rejects %s', (_name, text) => {
    expect(parseServerFrame(text)).toBeNull();
  });
});

describe('parseSnapshotBody', () => {
  it('adds the frame type to a REST snapshot body', () => {
    const frame = parseSnapshotBody(JSON.stringify({ v: 8, committedAtMs: 1, flags: [flag], segments: [] }));
    expect(frame).toMatchObject({ t: 'snapshot', v: 8 });
  });

  it('rejects bodies that are not snapshots', () => {
    expect(parseSnapshotBody('nope')).toBeNull();
    expect(parseSnapshotBody('{"v":1}')).toBeNull();
    expect(parseSnapshotBody('[]')).toBeNull();
  });
});
