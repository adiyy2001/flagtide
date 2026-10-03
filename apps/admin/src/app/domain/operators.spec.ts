import { describe, expect, it } from 'vitest';
import {
  formatValues,
  inferScalarType,
  operatorInfo,
  OPERATORS,
  parseConditionValues,
  splitValues,
} from './operators';

describe('operators', () => {
  it('describes every operator of the evaluation spec once', () => {
    expect(OPERATORS.map((info) => info.operator).sort()).toEqual(
      [
        'contains',
        'equals',
        'gt',
        'gte',
        'in',
        'lt',
        'lte',
        'semverEquals',
        'semverGt',
        'semverGte',
        'semverLt',
        'semverLte',
        'startsWith',
      ].sort(),
    );
  });

  it('throws for an operator it does not know', () => {
    expect(() => operatorInfo('nope' as never)).toThrow('unknown operator');
  });

  it('splits on commas, trims and drops empty tokens', () => {
    expect(splitValues(' a, b ,, c ,')).toEqual(['a', 'b', 'c']);
    expect(splitValues('   ')).toEqual([]);
  });

  it('formats values back to text and infers the scalar type of the first value', () => {
    expect(formatValues(['a', 2, true])).toBe('a, 2, true');
    expect(inferScalarType([3])).toBe('number');
    expect(inferScalarType([false])).toBe('boolean');
    expect(inferScalarType(['x'])).toBe('string');
    expect(inferScalarType([])).toBe('string');
  });

  describe('parseConditionValues', () => {
    it('keeps strings for equals and in', () => {
      expect(parseConditionValues('equals', 'string', 'pro')).toEqual({ values: ['pro'] });
      expect(parseConditionValues('in', 'string', 'pro, team')).toEqual({ values: ['pro', 'team'] });
    });

    it('parses numbers and booleans when the value type says so', () => {
      expect(parseConditionValues('in', 'number', '1, 2.5, -3, 1e3')).toEqual({ values: [1, 2.5, -3, 1000] });
      expect(parseConditionValues('equals', 'boolean', 'true')).toEqual({ values: [true] });
      expect(parseConditionValues('equals', 'boolean', 'false')).toEqual({ values: [false] });
    });

    it('rejects text that is not the chosen type', () => {
      expect(parseConditionValues('equals', 'number', 'abc')).toEqual({ error: 'abc is not a number' });
      expect(parseConditionValues('equals', 'boolean', 'yes')).toEqual({ error: 'yes is not true or false' });
      expect(parseConditionValues('equals', 'number', 'Infinity')).toEqual({
        error: 'Infinity is not a number',
      });
    });

    it('requires exactly one value for single value operators and at least one for the others', () => {
      expect(parseConditionValues('equals', 'string', '')).toEqual({ error: 'Enter a value' });
      expect(parseConditionValues('in', 'string', '')).toEqual({ error: 'Enter at least one value' });
      expect(parseConditionValues('equals', 'string', 'a, b')).toEqual({
        error: 'This operator takes exactly one value',
      });
    });

    it('treats contains and starts with values as text whatever the value type', () => {
      expect(parseConditionValues('contains', 'number', '12, ab')).toEqual({ values: ['12', 'ab'] });
      expect(parseConditionValues('startsWith', 'boolean', 'true')).toEqual({ values: ['true'] });
    });

    it('parses numbers for comparison operators and ignores the value type', () => {
      expect(parseConditionValues('gte', 'string', '18')).toEqual({ values: [18] });
      expect(parseConditionValues('lt', 'string', 'eighteen')).toEqual({ error: 'eighteen is not a number' });
    });

    it('validates semantic versions including pre-release tags', () => {
      expect(parseConditionValues('semverGte', 'string', '2.0.0-rc.1')).toEqual({ values: ['2.0.0-rc.1'] });
      expect(parseConditionValues('semverLt', 'string', '2.0')).toEqual({
        error: '2.0 is not a semantic version such as 2.1.0 or 2.0.0-rc.1',
      });
      expect(parseConditionValues('semverEquals', 'string', '1.0.0, 2.0.0')).toEqual({
        error: 'This operator takes exactly one value',
      });
    });
  });
});
