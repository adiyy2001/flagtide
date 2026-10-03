import { describe, expect, it } from 'vitest';
import { InvalidFlagConfigError, evaluate, indexSegments } from './evaluate.js';
import type { FlagConfig, Rule, Serve } from './types.js';

function flag(overrides: Partial<FlagConfig> = {}): FlagConfig {
  return {
    key: 'flag',
    type: 'boolean',
    enabled: true,
    killSwitch: false,
    salt: 'a1b2c3',
    variants: [
      { key: 'on', value: true },
      { key: 'off', value: false },
    ],
    offVariant: 'off',
    rules: [],
    fallthrough: { variant: 'off' },
    ...overrides,
  };
}

function ruleServing(serve: Serve, ...conditions: Rule['conditions']): Rule {
  return { id: 'r', conditions, serve };
}

describe('evaluate', () => {
  it('serves the fallthrough of a flag without rules', () => {
    expect(evaluate(flag({ fallthrough: { variant: 'on' } }), { key: 'u', attributes: {} })).toEqual({
      variantKey: 'on',
      value: true,
      reason: 'FALLTHROUGH',
      ruleIndex: null,
      ruleId: null,
      bucket: null,
    });
  });

  it('throws on a rollout whose weights do not cover the bucket', () => {
    const broken = flag({ fallthrough: { rollout: [{ variant: 'on', weight: 0 }] } });
    expect(() => evaluate(broken, { key: 'u', attributes: {} })).toThrow(InvalidFlagConfigError);
  });

  it('throws when a served variant does not exist', () => {
    const broken = flag({ fallthrough: { variant: 'ghost' } });
    expect(() => evaluate(broken, { key: 'u', attributes: {} })).toThrow(InvalidFlagConfigError);
  });

  it('treats attributes that are not finite numbers as missing', () => {
    const config = flag({
      rules: [ruleServing({ variant: 'on' }, { attribute: 'n', operator: 'gte', values: [0] })],
    });
    const result = evaluate(config, { key: 'u', attributes: { n: Number.POSITIVE_INFINITY } });
    expect(result.reason).toBe('FALLTHROUGH');
  });

  it('reads attributes named like object prototype members as ordinary attributes', () => {
    const config = flag({
      rules: [ruleServing({ variant: 'on' }, { attribute: '__proto__', operator: 'equals', values: ['x'] })],
    });
    const parsed = JSON.parse('{"key":"u","attributes":{"__proto__":"x"}}') as {
      key: string;
      attributes: Record<string, unknown>;
    };
    expect(evaluate(config, parsed).reason).toBe('RULE_MATCH');
    expect(evaluate(config, { key: 'u', attributes: {} }).reason).toBe('FALLTHROUGH');
  });

  it('does not read inherited properties as attributes', () => {
    const config = flag({
      rules: [
        ruleServing(
          { variant: 'on' },
          { attribute: 'constructor', operator: 'contains', values: ['Object'] },
        ),
      ],
    });
    expect(evaluate(config, { key: 'u', attributes: {} }).reason).toBe('FALLTHROUGH');
  });

  it('indexes segments by key', () => {
    const index = indexSegments([
      { key: 'a', included: [], excluded: [], rules: [] },
      { key: 'b', included: [], excluded: [], rules: [] },
    ]);
    expect(Array.from(index.keys())).toEqual(['a', 'b']);
  });

  it('serves a segment member through a rule', () => {
    const config = flag({ rules: [ruleServing({ variant: 'on' }, { segment: 'beta' })] });
    const segments = indexSegments([{ key: 'beta', included: ['u1'], excluded: [], rules: [] }]);
    expect(evaluate(config, { key: 'u1', attributes: {} }, segments).reason).toBe('RULE_MATCH');
    expect(evaluate(config, { key: 'u2', attributes: {} }, segments).reason).toBe('FALLTHROUGH');
  });
});
