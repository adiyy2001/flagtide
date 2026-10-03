import { describe, expect, it } from 'vitest';
import type { JsonValue } from '@flagwire/core';
import { DEFAULT_PROMO, parsePromo } from './promo';

const unusable: JsonValue[] = [null, 'text', 4, true, [1, 2], {}, { headline: '  ' }, { headline: 7 }];

describe('parsePromo', () => {
  it('reads a complete promo', () => {
    expect(parsePromo({ headline: ' Sale ', code: ' X1 ', discountPercent: 15 })).toEqual({
      headline: 'Sale',
      code: 'X1',
      discountPercent: 15,
    });
  });

  it.each(unusable)('uses the default for %j', (value) => {
    expect(parsePromo(value)).toBe(DEFAULT_PROMO);
  });

  it('drops a code that is not text and a discount outside 1 to 100', () => {
    expect(parsePromo({ headline: 'A', code: 3, discountPercent: 0 })).toEqual({
      headline: 'A',
      code: '',
      discountPercent: 0,
    });
    expect(parsePromo({ headline: 'A', discountPercent: 101 }).discountPercent).toBe(0);
    expect(parsePromo({ headline: 'A', discountPercent: -5 }).discountPercent).toBe(0);
    expect(parsePromo({ headline: 'A', discountPercent: '10' }).discountPercent).toBe(0);
  });
});
