import { describe, expect, it } from 'vitest';
import { murmur3x86_32, murmur3x86_32OfText } from './murmur3.js';

function bytes(...values: number[]): Uint8Array {
  return Uint8Array.from(values);
}

describe('murmur3x86_32', () => {
  const publishedVectors: ReadonlyArray<readonly [string, Uint8Array, number, number]> = [
    ['empty, seed 0', bytes(), 0, 0x00000000],
    ['empty, seed 1', bytes(), 1, 0x514e28b7],
    ['empty, seed 0xffffffff', bytes(), 0xffffffff, 0x81f16f39],
    ['ff ff ff ff', bytes(0xff, 0xff, 0xff, 0xff), 0, 0x76293b50],
    ['21 43 65 87', bytes(0x21, 0x43, 0x65, 0x87), 0, 0xf55b516b],
    ['21 43 65 87, seed 0x5082edee', bytes(0x21, 0x43, 0x65, 0x87), 0x5082edee, 0x2362f9de],
    ['21 43 65', bytes(0x21, 0x43, 0x65), 0, 0x7e4a8634],
    ['21 43', bytes(0x21, 0x43), 0, 0xa0f7b07a],
    ['21', bytes(0x21), 0, 0x72661cf4],
    ['four zero bytes', bytes(0, 0, 0, 0), 0, 0x2362f9de],
    ['three zero bytes', bytes(0, 0, 0), 0, 0x85f0b427],
    ['two zero bytes', bytes(0, 0), 0, 0x30f4c306],
    ['one zero byte', bytes(0), 0, 0x514e28b7],
  ];

  it.each(publishedVectors)('matches the published vector for %s', (_name, input, seed, expected) => {
    expect(murmur3x86_32(input, seed)).toBe(expected);
  });

  const textVectors: ReadonlyArray<readonly [string, number, number]> = [
    ['test', 0, 0xba6bd213],
    ['Hello, world!', 0, 0xc0363e43],
    ['The quick brown fox jumps over the lazy dog', 0, 0x2e4ff723],
    ['Hello, world!', 0x9747b28c, 0x24884cba],
    ['The quick brown fox jumps over the lazy dog', 0x9747b28c, 0x2fa826cd],
    ['aaaa', 0x9747b28c, 0x5a97808a],
    ['aaa', 0x9747b28c, 0x283e0130],
    ['aa', 0x9747b28c, 0x5d211726],
    ['a', 0x9747b28c, 0x7fa09ea6],
    ['abcd', 0x9747b28c, 0xf0478627],
    ['abc', 0x9747b28c, 0xc84a62dd],
    ['ab', 0x9747b28c, 0x74875592],
  ];

  it.each(textVectors)('matches the published vector for "%s" with seed %i', (text, seed, expected) => {
    expect(murmur3x86_32OfText(text, seed)).toBe(expected);
  });

  it('reproduces the SMHasher verification value 0xB0F57EE3', () => {
    const collected = new Uint8Array(1024);
    const view = new DataView(collected.buffer);
    for (let length = 0; length < 256; length++) {
      const key = Uint8Array.from({ length }, (_, index) => index);
      view.setUint32(length * 4, murmur3x86_32(key, 256 - length), true);
    }
    expect(murmur3x86_32(collected, 0)).toBe(0xb0f57ee3);
  });

  it('returns an unsigned 32 bit integer', () => {
    for (let index = 0; index < 1000; index++) {
      const hash = murmur3x86_32OfText(`key-${index}`);
      expect(Number.isInteger(hash)).toBe(true);
      expect(hash).toBeGreaterThanOrEqual(0);
      expect(hash).toBeLessThanOrEqual(0xffffffff);
    }
  });

  it('defaults the seed to zero', () => {
    expect(murmur3x86_32OfText('test')).toBe(murmur3x86_32OfText('test', 0));
  });
});
