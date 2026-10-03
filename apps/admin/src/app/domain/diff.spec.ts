import { describe, expect, it } from 'vitest';
import { diffJson, flatten } from './diff';

describe('diffJson', () => {
  it('flattens nested objects and arrays into dotted paths', () => {
    expect([...flatten({ a: { b: [1, { c: 2 }] }, d: [], e: {} })]).toEqual([
      ['a.b.0', '1'],
      ['a.b.1.c', '2'],
      ['d', '[]'],
      ['e', '{}'],
    ]);
  });

  it('reports added, removed and changed leaves in a natural order', () => {
    const lines = diffJson(
      { enabled: false, rules: [{ id: 'a' }, { id: 'b' }], gone: 1 },
      { enabled: true, rules: [{ id: 'a' }, { id: 'b' }, { id: 'c' }], fresh: 'x' },
    );
    expect(lines).toEqual([
      { path: 'enabled', kind: 'changed', before: 'false', after: 'true' },
      { path: 'fresh', kind: 'added', before: null, after: '"x"' },
      { path: 'gone', kind: 'removed', before: '1', after: null },
      { path: 'rules.2.id', kind: 'added', before: null, after: '"c"' },
    ]);
  });

  it('sorts numeric path segments by number', () => {
    const lines = diffJson(null, { rules: Array.from({ length: 11 }, (_, index) => index) });
    expect(lines.map((line) => line.path).slice(0, 3)).toEqual(['rules.0', 'rules.1', 'rules.2']);
    expect(lines.at(-1)?.path).toBe('rules.10');
  });

  it('reports a scalar replaced at the root', () => {
    expect(diffJson(1, 2)).toEqual([{ path: '(value)', kind: 'changed', before: '1', after: '2' }]);
  });

  it('is empty when nothing differs', () => {
    expect(diffJson({ a: 1 }, { a: 1 })).toEqual([]);
    expect(diffJson(undefined, undefined)).toEqual([]);
  });
});
