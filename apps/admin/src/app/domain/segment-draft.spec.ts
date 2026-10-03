import { describe, expect, it } from 'vitest';
import { newCondition } from './drafts';
import type { SegmentModel } from './models';
import {
  emptySegmentDraft,
  newGroup,
  segmentBody,
  segmentDraftOf,
  serializeSegment,
  splitContextKeys,
  validateSegment,
} from './segment-draft';

const segment: SegmentModel = {
  key: 'beta-users',
  name: 'Beta users',
  revision: 3,
  environment: 'dev',
  updatedAt: '2026-10-03T10:00:00Z',
  included: ['u1', 'u2'],
  excluded: ['u9'],
  rules: [[{ attribute: 'country', operator: 'in', values: ['PL', 'DE'] }]],
};

describe('segment drafts', () => {
  it('splits context keys on lines and commas without duplicates', () => {
    expect(splitContextKeys('a\nb, c\n\n a ,')).toEqual(['a', 'b', 'c']);
    expect(splitContextKeys('')).toEqual([]);
  });

  it('round trips a segment through a draft and a request body', () => {
    const body = segmentBody(segmentDraftOf(segment));
    expect(body).toEqual({
      name: 'Beta users',
      included: ['u1', 'u2'],
      excluded: ['u9'],
      rules: [[{ attribute: 'country', operator: 'in', values: ['PL', 'DE'], negate: false }]],
    });
  });

  it('serializes independently of generated ids and notices edits', () => {
    const first = segmentDraftOf(segment);
    const second = segmentDraftOf(segment);
    expect(serializeSegment(first)).toBe(serializeSegment(second));
    expect(serializeSegment({ ...first, name: 'Other' })).not.toBe(serializeSegment(second));
  });

  it('validates a new segment', () => {
    const draft = { ...emptySegmentDraft(), key: 'New Key' };
    expect(validateSegment(draft, true, []).map((issue) => issue.path)).toEqual(['key', 'name']);
    expect(validateSegment({ ...draft, key: 'ok', name: 'Fine' }, true, [])).toEqual([]);
    expect(validateSegment({ ...draft, key: 'ok', name: 'Fine' }, true, ['ok'])[0]?.message).toBe(
      'A segment with this key already exists',
    );
  });

  it('does not check the key of an existing segment', () => {
    expect(validateSegment({ ...emptySegmentDraft(), name: 'Named' }, false, [])).toEqual([]);
  });

  it('rejects a context key that is both included and excluded', () => {
    const draft = { ...emptySegmentDraft(), key: 'a', name: 'A', included: 'u1, u2', excluded: 'u2' };
    expect(validateSegment(draft, true, [])).toEqual([
      { path: 'excluded', message: 'u2 is both included and excluded' },
    ]);
  });

  it('validates every condition of every group', () => {
    const group = newGroup();
    const draft = {
      ...emptySegmentDraft(),
      key: 'a',
      name: 'A',
      groups: [
        {
          ...group,
          conditions: [
            { ...newCondition('attribute'), attribute: 'x', operator: 'gt' as const, valuesText: 'abc' },
          ],
        },
      ],
    };
    expect(validateSegment(draft, true, []).map((issue) => issue.path)).toEqual([
      'groups.0.conditions.0.values',
    ]);
  });
});
