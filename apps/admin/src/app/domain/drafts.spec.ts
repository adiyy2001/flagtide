import { describe, expect, it } from 'vitest';
import { booleanFlag, environmentConfig, rule } from '../../test-support/fixtures';
import {
  attributeConditionDraftOf,
  definitionDraftOf,
  environmentDraftOf,
  fixedServeDraft,
  moveItem,
  newCondition,
  newRuleDraft,
  newVariantDraft,
  nextUid,
  removeAt,
  replaceAt,
  rolloutServeDraft,
  serializeDefinition,
  serializeEnvironment,
  serveDraftOf,
} from './drafts';

describe('drafts', () => {
  it('hands out unique ids', () => {
    expect(nextUid('x')).not.toBe(nextUid('x'));
  });

  it('turns a flag definition into editable text', () => {
    const draft = definitionDraftOf(booleanFlag('a', { description: 'hello' }));
    expect(draft.description).toBe('hello');
    expect(draft.variants.map((variant) => [variant.key, variant.valueText])).toEqual([
      ['on', 'true'],
      ['off', 'false'],
    ]);
  });

  it('proposes a free variant key per type', () => {
    expect(newVariantDraft('number', ['variant-3']).key).toBe('variant-2');
    expect(newVariantDraft('number', ['variant-2']).key).toBe('variant-3');
    expect(newVariantDraft('json', []).valueText).toBe('{}');
    expect(newVariantDraft('boolean', []).valueText).toBe('true');
  });

  it('keeps fixed serves and rollouts apart', () => {
    expect(serveDraftOf({ variant: 'on' })).toMatchObject({ mode: 'variant', variant: 'on', rollout: [] });
    const rolloutDraft = serveDraftOf({
      rollout: [
        { variant: 'on', weight: 12500 },
        { variant: 'off', weight: 87500 },
      ],
    });
    expect(rolloutDraft.mode).toBe('rollout');
    expect(rolloutDraft.rollout.map((entry) => [entry.variant, entry.percent])).toEqual([
      ['on', '12.5'],
      ['off', '87.5'],
    ]);
  });

  it('starts a rollout with two variants split evenly', () => {
    const draft = rolloutServeDraft(['a', 'b', 'c']);
    expect(draft.rollout.map((entry) => [entry.variant, entry.percent])).toEqual([
      ['a', '50'],
      ['b', '50'],
    ]);
    expect(rolloutServeDraft([]).rollout).toEqual([]);
    expect(fixedServeDraft('a')).toEqual({ mode: 'variant', variant: 'a', rollout: [] });
  });

  it('infers the value type of attribute conditions and keeps segment conditions', () => {
    const draft = environmentDraftOf(
      environmentConfig({
        rules: [
          {
            id: 'r',
            conditions: [
              { attribute: 'age', operator: 'gte', values: [18], negate: true },
              { segment: 'beta' },
              { attribute: 'beta', operator: 'equals', values: [true] },
            ],
            serve: { variant: 'on' },
          },
        ],
      }),
    );
    const [age, segment, flagged] = draft.rules[0]?.conditions ?? [];
    expect(age).toMatchObject({ kind: 'attribute', scalarType: 'number', valuesText: '18', negate: true });
    expect(segment).toMatchObject({ kind: 'segment', segment: 'beta', negate: false });
    expect(flagged).toMatchObject({ scalarType: 'boolean', valuesText: 'true' });
    expect(attributeConditionDraftOf({ attribute: 'x', operator: 'in', values: ['a', 'b'] }).valuesText).toBe(
      'a, b',
    );
  });

  it('creates rules with a free name and a blank condition', () => {
    const draft = newRuleDraft(['rule-1', 'rule-3'], 'on');
    expect(draft.id).toBe('rule-2');
    expect(draft.conditions).toHaveLength(1);
    expect(draft.serve.variant).toBe('on');
    expect(newCondition('segment', 'beta')).toMatchObject({ kind: 'segment', segment: 'beta' });
  });

  it('moves, replaces and removes items without touching the input', () => {
    const items = ['a', 'b', 'c'];
    expect(moveItem(items, 0, 2)).toEqual(['b', 'c', 'a']);
    expect(moveItem(items, 2, 1)).toEqual(['a', 'c', 'b']);
    expect(moveItem(items, 1, 1)).toEqual(items);
    expect(moveItem(items, -1, 1)).toEqual(items);
    expect(moveItem(items, 0, 3)).toEqual(items);
    expect(replaceAt(items, 1, 'x')).toEqual(['a', 'x', 'c']);
    expect(removeAt(items, 0)).toEqual(['b', 'c']);
    expect(items).toEqual(['a', 'b', 'c']);
  });

  it('serializes drafts independently of generated ids', () => {
    const flag = booleanFlag('a', { environments: { dev: environmentConfig({ rules: [rule('r1')] }) } });
    const first = environmentDraftOf(flag.environments['dev'] ?? environmentConfig());
    const second = environmentDraftOf(flag.environments['dev'] ?? environmentConfig());
    expect(first.rules[0]?.uid).not.toBe(second.rules[0]?.uid);
    expect(serializeEnvironment(first)).toBe(serializeEnvironment(second));
    expect(serializeDefinition(definitionDraftOf(flag))).toBe(serializeDefinition(definitionDraftOf(flag)));
  });

  it('serializes differently when an edit changes anything that is saved', () => {
    const base = environmentDraftOf(environmentConfig({ rules: [rule('r1')] }));
    const edited = { ...base, enabled: !base.enabled };
    expect(serializeEnvironment(edited)).not.toBe(serializeEnvironment(base));
    const firstRule = base.rules[0];
    if (firstRule === undefined) {
      throw new Error('rule missing');
    }
    const renamed = { ...base, rules: [{ ...firstRule, id: 'other' }] };
    expect(serializeEnvironment(renamed)).not.toBe(serializeEnvironment(base));
  });
});
