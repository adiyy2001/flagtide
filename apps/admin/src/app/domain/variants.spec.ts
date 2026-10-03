import { describe, expect, it } from 'vitest';
import { defaultVariants, formatVariantValue, isSlug, parseVariantValue } from './variants';

describe('variants', () => {
  it('accepts slugs and rejects dots, capitals and empty keys', () => {
    expect(isSlug('new-checkout_2')).toBe(true);
    expect(isSlug('a.b')).toBe(false);
    expect(isSlug('Upper')).toBe(false);
    expect(isSlug('')).toBe(false);
    expect(isSlug('-dash')).toBe(false);
    expect(isSlug('a'.repeat(65))).toBe(false);
  });

  it('parses a value for each flag type', () => {
    expect(parseVariantValue('boolean', 'true')).toEqual({ value: true });
    expect(parseVariantValue('boolean', 'maybe')).toEqual({ error: 'Choose true or false' });
    expect(parseVariantValue('string', '')).toEqual({ value: '' });
    expect(parseVariantValue('number', ' 12.5 ')).toEqual({ value: 12.5 });
    expect(parseVariantValue('number', 'x')).toEqual({ error: 'Enter a number' });
    expect(parseVariantValue('json', '{"a":[1,2]}')).toEqual({ value: { a: [1, 2] } });
    expect(parseVariantValue('json', 'null')).toEqual({ value: null });
  });

  it('reports why JSON is invalid', () => {
    const result = parseVariantValue('json', '{oops');
    expect(result).toHaveProperty('error');
    expect('error' in result && result.error.startsWith('Not valid JSON: ')).toBe(true);
  });

  it('formats values as editable text', () => {
    expect(formatVariantValue('boolean', false)).toBe('false');
    expect(formatVariantValue('string', 'Buy now')).toBe('Buy now');
    expect(formatVariantValue('number', 50)).toBe('50');
    expect(formatVariantValue('json', { a: 1 })).toBe('{\n  "a": 1\n}');
  });

  it.each(['boolean', 'string', 'number', 'json'] as const)(
    'offers defaults that are valid for %s flags',
    (type) => {
      const variants = defaultVariants(type);
      expect(variants).toHaveLength(2);
      for (const variant of variants) {
        expect(isSlug(variant.key)).toBe(true);
        expect(parseVariantValue(type, variant.valueText)).toHaveProperty('value');
      }
    },
  );
});
