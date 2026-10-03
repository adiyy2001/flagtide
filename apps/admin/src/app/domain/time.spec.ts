import { describe, expect, it } from 'vitest';
import { formatTimestamp } from './time';

describe('formatTimestamp', () => {
  it('formats an ISO timestamp in UTC', () => {
    expect(formatTimestamp('2026-10-03T10:05:09Z')).toContain('UTC');
    expect(formatTimestamp('2026-10-03T10:05:09Z')).toContain('10:05:09');
  });

  it('returns the input when it is not a date', () => {
    expect(formatTimestamp('yesterday')).toBe('yesterday');
  });
});
