import fc from 'fast-check';
import { describe, expect, it } from 'vitest';
import { bucketOf } from './bucket.js';
import { evaluate } from './evaluate.js';
import type { FlagConfig } from './types.js';

const BINS = 100;
const KEYS = 1_000_000;
const Z_FOR_ALPHA_1E_4 = 3.719;

function chiSquareCriticalValue(degreesOfFreedom: number): number {
  const shape = 2 / (9 * degreesOfFreedom);
  return degreesOfFreedom * (1 - shape + Z_FOR_ALPHA_1E_4 * Math.sqrt(shape)) ** 3;
}

function rolloutFlag(onWeight: number): FlagConfig {
  return {
    key: 'rollout',
    type: 'boolean',
    enabled: true,
    killSwitch: false,
    salt: 'c0ffee',
    variants: [
      { key: 'on', value: true },
      { key: 'off', value: false },
    ],
    offVariant: 'off',
    rules: [],
    fallthrough: {
      rollout: [
        { variant: 'on', weight: onWeight },
        { variant: 'off', weight: 100000 - onWeight },
      ],
    },
  };
}

describe('bucketing properties', () => {
  it('is uniform over one million keys (chi-square)', () => {
    const counts = new Array<number>(BINS).fill(0);
    for (let index = 0; index < KEYS; index++) {
      counts[Math.floor(bucketOf('uniformity', 'ab12', `user-${index}`) / (100000 / BINS))]++;
    }
    const expected = KEYS / BINS;
    const statistic = counts.reduce((sum, count) => sum + (count - expected) ** 2 / expected, 0);
    expect(statistic).toBeLessThan(chiSquareCriticalValue(BINS - 1));
  });

  it('gives the same context the same result every time', () => {
    fc.assert(
      fc.property(
        fc.string({ unit: 'binary' }),
        fc.integer({ min: 0, max: 100000 }),
        (contextKey, onWeight) => {
          const config = rolloutFlag(onWeight);
          const context = { key: contextKey, attributes: {} };
          expect(evaluate(config, context)).toEqual(evaluate(config, context));
        },
      ),
      { numRuns: 2000 },
    );
  });

  it('never drops a context when a boolean rollout grows', () => {
    fc.assert(
      fc.property(
        fc.string({ unit: 'binary' }),
        fc.integer({ min: 0, max: 100000 }),
        fc.integer({ min: 0, max: 100000 }),
        (contextKey, first, second) => {
          const lower = Math.min(first, second);
          const higher = Math.max(first, second);
          const context = { key: contextKey, attributes: {} };
          const before = evaluate(rolloutFlag(lower), context).variantKey;
          const after = evaluate(rolloutFlag(higher), context).variantKey;
          if (before === 'on') {
            expect(after).toBe('on');
          }
        },
      ),
      { numRuns: 5000 },
    );
  });
});
