import { describe, expect, it } from 'vitest';
import { BUCKET_SPACE, bucketOf } from './bucket';
import { murmur3x86_32OfText } from './murmur3';

describe('bucketOf', () => {
  it('hashes flagKey.salt.contextKey and takes the unsigned value modulo 100000', () => {
    const expected = murmur3x86_32OfText('new-checkout.9f2c41.user-42') % 100000;
    expect(bucketOf('new-checkout', '9f2c41', 'user-42')).toBe(expected);
  });

  it('stays inside the bucket space', () => {
    for (let index = 0; index < 5000; index++) {
      const bucket = bucketOf('flag', 'a1', `key-${index}`);
      expect(bucket).toBeGreaterThanOrEqual(0);
      expect(bucket).toBeLessThan(BUCKET_SPACE);
    }
  });

  it('is sensitive to flag key, salt and context key', () => {
    const base = bucketOf('flag', 'a1', 'user');
    expect(bucketOf('flag2', 'a1', 'user')).not.toBe(base);
    expect(bucketOf('flag', 'a2', 'user')).not.toBe(base);
    expect(bucketOf('flag', 'a1', 'user2')).not.toBe(base);
  });

  it('hashes a lone surrogate like the replacement character', () => {
    expect(bucketOf('flag', 'a1', '\ud800')).toBe(bucketOf('flag', 'a1', '�'));
  });

  it('does not normalize unicode', () => {
    expect(bucketOf('flag', 'a1', 'café')).not.toBe(bucketOf('flag', 'a1', 'café'));
  });
});
