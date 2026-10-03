import { murmur3x86_32OfText } from './murmur3';

export const BUCKET_SPACE = 100000;

export function bucketOf(flagKey: string, salt: string, contextKey: string): number {
  return murmur3x86_32OfText(`${flagKey}.${salt}.${contextKey}`) % BUCKET_SPACE;
}
