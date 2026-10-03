const REPLACEMENT_CHARACTER = 0xfffd;

function isHighSurrogate(unit: number): boolean {
  return unit >= 0xd800 && unit <= 0xdbff;
}

function isLowSurrogate(unit: number): boolean {
  return unit >= 0xdc00 && unit <= 0xdfff;
}

function codePointAt(text: string, index: number): { codePoint: number; units: number } {
  const unit = text.charCodeAt(index);
  if (isHighSurrogate(unit)) {
    const next = index + 1 < text.length ? text.charCodeAt(index + 1) : 0;
    if (isLowSurrogate(next)) {
      return { codePoint: 0x10000 + ((unit - 0xd800) << 10) + (next - 0xdc00), units: 2 };
    }
    return { codePoint: REPLACEMENT_CHARACTER, units: 1 };
  }
  if (isLowSurrogate(unit)) {
    return { codePoint: REPLACEMENT_CHARACTER, units: 1 };
  }
  return { codePoint: unit, units: 1 };
}

function encodedLength(codePoint: number): number {
  if (codePoint < 0x80) {
    return 1;
  }
  if (codePoint < 0x800) {
    return 2;
  }
  return codePoint < 0x10000 ? 3 : 4;
}

/** Number of bytes `text` takes in UTF-8, counting a lone surrogate as the three bytes of U+FFFD. */
export function utf8Length(text: string): number {
  let length = 0;
  let index = 0;
  while (index < text.length) {
    const unit = text.charCodeAt(index);
    if (unit < 0x80) {
      length += 1;
      index += 1;
    } else {
      const { codePoint, units } = codePointAt(text, index);
      length += encodedLength(codePoint);
      index += units;
    }
  }
  return length;
}

/** Encodes text as UTF-8. A lone surrogate becomes U+FFFD, the same as `TextEncoder` and unlike Java's `getBytes`. */
export function encodeUtf8(text: string): Uint8Array {
  const bytes = new Uint8Array(utf8Length(text));
  let offset = 0;
  let index = 0;
  while (index < text.length) {
    const { codePoint, units } = codePointAt(text, index);
    index += units;
    if (codePoint < 0x80) {
      bytes[offset++] = codePoint;
    } else if (codePoint < 0x800) {
      bytes[offset++] = 0xc0 | (codePoint >> 6);
      bytes[offset++] = 0x80 | (codePoint & 0x3f);
    } else if (codePoint < 0x10000) {
      bytes[offset++] = 0xe0 | (codePoint >> 12);
      bytes[offset++] = 0x80 | ((codePoint >> 6) & 0x3f);
      bytes[offset++] = 0x80 | (codePoint & 0x3f);
    } else {
      bytes[offset++] = 0xf0 | (codePoint >> 18);
      bytes[offset++] = 0x80 | ((codePoint >> 12) & 0x3f);
      bytes[offset++] = 0x80 | ((codePoint >> 6) & 0x3f);
      bytes[offset++] = 0x80 | (codePoint & 0x3f);
    }
  }
  return bytes;
}
