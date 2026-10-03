import fc from 'fast-check';
import { describe, expect, it } from 'vitest';
import { encodeUtf8, utf8Length } from './utf8.js';

const nativeEncoder = new TextEncoder();

describe('encodeUtf8', () => {
  it('agrees with TextEncoder on every well formed string', () => {
    fc.assert(
      fc.property(fc.string({ unit: 'binary', maxLength: 64 }), (text) => {
        const wellFormed = text.toWellFormed();
        expect(Array.from(encodeUtf8(wellFormed))).toEqual(Array.from(nativeEncoder.encode(wellFormed)));
      }),
      { numRuns: 2000 },
    );
  });

  it('agrees with TextEncoder on lone surrogates, which become U+FFFD', () => {
    fc.assert(
      fc.property(fc.string({ unit: 'binary', maxLength: 64 }), (text) => {
        expect(Array.from(encodeUtf8(text))).toEqual(Array.from(nativeEncoder.encode(text)));
      }),
      { numRuns: 2000 },
    );
  });

  it('encodes the boundaries of every sequence length', () => {
    expect(Array.from(encodeUtf8('\u007f'))).toEqual([0x7f]);
    expect(Array.from(encodeUtf8('\u0080'))).toEqual([0xc2, 0x80]);
    expect(Array.from(encodeUtf8('߿'))).toEqual([0xdf, 0xbf]);
    expect(Array.from(encodeUtf8('ࠀ'))).toEqual([0xe0, 0xa0, 0x80]);
    expect(Array.from(encodeUtf8('￿'))).toEqual([0xef, 0xbf, 0xbf]);
    expect(Array.from(encodeUtf8('\u{10000}'))).toEqual([0xf0, 0x90, 0x80, 0x80]);
    expect(Array.from(encodeUtf8('\u{10ffff}'))).toEqual([0xf4, 0x8f, 0xbf, 0xbf]);
  });

  it('writes one replacement character per lone surrogate', () => {
    expect(Array.from(encodeUtf8('\ud800'))).toEqual([0xef, 0xbf, 0xbd]);
    expect(Array.from(encodeUtf8('\udc00'))).toEqual([0xef, 0xbf, 0xbd]);
    expect(Array.from(encodeUtf8('\ude00\ud83d'))).toEqual([0xef, 0xbf, 0xbd, 0xef, 0xbf, 0xbd]);
    expect(Array.from(encodeUtf8('a\ud800b'))).toEqual([0x61, 0xef, 0xbf, 0xbd, 0x62]);
  });

  it('measures the encoded length', () => {
    expect(utf8Length('')).toBe(0);
    expect(utf8Length('zażółć')).toBe(nativeEncoder.encode('zażółć').length);
    expect(utf8Length('\ud800')).toBe(3);
  });
});
