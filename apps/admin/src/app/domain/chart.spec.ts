import { describe, expect, it } from 'vitest';
import { appendPoint, linePath, niceMax } from './chart';

describe('chart helpers', () => {
  it('keeps only the newest points', () => {
    expect(appendPoint([1, 2, 3], 4, 3)).toEqual([2, 3, 4]);
    expect(appendPoint([], 1)).toEqual([1]);
  });

  it('rounds the scale up to a readable step', () => {
    expect(niceMax(0)).toBe(10);
    expect(niceMax(37)).toBe(50);
    expect(niceMax(300)).toBe(300);
    expect(niceMax(301)).toBe(500);
    expect(niceMax(7400)).toBe(8000);
  });

  it('draws a path from the right edge so a fresh chart fills from the right', () => {
    expect(linePath([0, 50], 100, 50, 100, 3)).toBe('M50.0 50.0 L100.0 25.0');
  });

  it('clamps values above the scale and returns nothing without data', () => {
    expect(linePath([500], 100, 40, 100, 2)).toBe('M100.0 0.0');
    expect(linePath([], 100, 40, 100)).toBe('');
    expect(linePath([1], 100, 40, 0)).toBe('');
  });
});
