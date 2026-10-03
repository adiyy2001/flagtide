import { murmur3x86_32OfText } from './murmur3.js';

/** Number of buckets a context key is hashed into. One unit is 0.001 percent. */
export const BUCKET_SPACE = 100000;

/** The bucket, from 0 to 99999, of a context key for a flag: MurmurHash3 over `flagKey.salt.contextKey` modulo 100000. */
export function bucketOf(flagKey: string, salt: string, contextKey: string): number {
  return murmur3x86_32OfText(`${flagKey}.${salt}.${contextKey}`) % BUCKET_SPACE;
}
