import { describe, expect, it } from 'vitest';
import { booleanFlag, variantFlag } from '../../test-support/fixtures.js';
import { flagTypeOf, jsonEquals, resolveFlag } from './resolve.js';

const context = { key: 'user-1', attributes: {} };
const noSegments = new Map();

describe('resolveFlag', () => {
  it('returns the fallback with FLAG_NOT_FOUND for an unknown flag', () => {
    expect(resolveFlag(undefined, context, noSegments, true)).toMatchObject({
      value: true,
      reason: 'FLAG_NOT_FOUND',
    });
  });

  it('returns the fallback with TYPE_MISMATCH when the flag has another type', () => {
    expect(resolveFlag(booleanFlag('a'), context, noSegments, 'text')).toMatchObject({
      value: 'text',
      reason: 'TYPE_MISMATCH',
    });
  });

  it('evaluates a flag of the matching type', () => {
    expect(resolveFlag(booleanFlag('a'), context, noSegments, false)).toMatchObject({
      value: true,
      reason: 'FALLTHROUGH',
      variantKey: 'on',
    });
  });

  it('reads string, number and json flags', () => {
    const text = variantFlag('t', 'string', [{ key: 'a', value: 'hello' }], 'a');
    const number = variantFlag('n', 'number', [{ key: 'a', value: 49.5 }], 'a');
    const json = variantFlag('j', 'json', [{ key: 'a', value: { banner: 'sale' } }], 'a');
    expect(resolveFlag(text, context, noSegments, '').value).toBe('hello');
    expect(resolveFlag(number, context, noSegments, 0).value).toBe(49.5);
    expect(resolveFlag(json, context, noSegments, {}).value).toEqual({ banner: 'sale' });
  });

  it('lets an override of the right type win, even for an unknown flag', () => {
    expect(resolveFlag(booleanFlag('a'), context, noSegments, true, false)).toMatchObject({
      value: false,
      reason: 'OVERRIDE',
    });
    expect(resolveFlag(undefined, context, noSegments, 'x', 'y')).toMatchObject({
      value: 'y',
      reason: 'OVERRIDE',
    });
  });

  it('ignores an override of the wrong type', () => {
    expect(resolveFlag(booleanFlag('a'), context, noSegments, true, 'nope').reason).toBe('FALLTHROUGH');
  });

  it('reports the kill switch', () => {
    expect(resolveFlag(booleanFlag('a', { killSwitch: true }), context, noSegments, true)).toMatchObject({
      value: false,
      reason: 'KILL_SWITCH',
    });
  });
});

describe('flagTypeOf', () => {
  it.each([
    [true, 'boolean'],
    ['a', 'string'],
    [1, 'number'],
    [null, 'json'],
    [[1], 'json'],
    [{ a: 1 }, 'json'],
  ] as const)('%j is %s', (value, type) => {
    expect(flagTypeOf(value)).toBe(type);
  });
});

describe('jsonEquals', () => {
  it.each([
    [1, 1, true],
    ['a', 'a', true],
    ['a', 'b', false],
    [null, null, true],
    [null, {}, false],
    [{ a: 1, b: [1, { c: 2 }] }, { b: [1, { c: 2 }], a: 1 }, true],
    [{ a: 1 }, { a: 2 }, false],
    [{ a: 1 }, { a: 1, b: 2 }, false],
    [{ a: 1, b: 2 }, { a: 1 }, false],
    [[1, 2], [2, 1], false],
    [[1], [1, 2], false],
    [[], {}, false],
    [{}, [], false],
    [1, '1', false],
  ] as const)('%j and %j: %s', (left, right, expected) => {
    expect(jsonEquals(left, right)).toBe(expected);
  });
});
