import { describe, expect, it } from 'vitest';
import {
  describeRemaining,
  formatPercent,
  parsePercent,
  remainingUnits,
  splitEvenly,
  sumUnits,
} from './rollout';

describe('rollout percentages', () => {
  it.each([
    ['0', 0],
    ['100', 100000],
    ['12.5', 12500],
    ['33.333', 33333],
    ['0.001', 1],
    [' 7 ', 7000],
  ])('parses %s to %i thousandths of a percent', (text, units) => {
    expect(parsePercent(text)).toBe(units);
  });

  it.each(['', 'abc', '-1', '100.001', '101', '1.2345', '1e2', '.5', '1.', '1,5'])('rejects %j', (text) => {
    expect(parsePercent(text)).toBeNull();
  });

  it('formats units without trailing zeros and round trips every unit boundary', () => {
    expect(formatPercent(0)).toBe('0');
    expect(formatPercent(100000)).toBe('100');
    expect(formatPercent(12500)).toBe('12.5');
    expect(formatPercent(1)).toBe('0.001');
    expect(formatPercent(33334)).toBe('33.334');
    for (const units of [0, 1, 999, 1000, 33333, 99999, 100000]) {
      expect(parsePercent(formatPercent(units))).toBe(units);
    }
  });

  it('sums valid entries and counts invalid ones as zero', () => {
    expect(sumUnits(['25', '75'])).toBe(100000);
    expect(sumUnits(['25', 'x'])).toBe(25000);
    expect(remainingUnits(['60', '30'])).toBe(10000);
    expect(remainingUnits(['60', '50'])).toBe(-10000);
  });

  it('describes what is left and what is over', () => {
    expect(describeRemaining(0)).toBe('Weights add up to 100%');
    expect(describeRemaining(10000)).toBe('10% still to assign');
    expect(describeRemaining(-2500)).toBe('Over by 2.5%');
  });

  it('splits evenly with the remainder on the first entry so the total is exactly 100', () => {
    expect(splitEvenly(2)).toEqual(['50', '50']);
    expect(splitEvenly(3)).toEqual(['33.334', '33.333', '33.333']);
    expect(sumUnits(splitEvenly(7))).toBe(100000);
    expect(splitEvenly(0)).toEqual([]);
  });
});
