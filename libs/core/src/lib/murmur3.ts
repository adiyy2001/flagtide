import { encodeUtf8 } from './utf8';

const C1 = 0xcc9e2d51;
const C2 = 0x1b873593;

function rotateLeft(value: number, shift: number): number {
  return (value << shift) | (value >>> (32 - shift));
}

function mixBlock(block: number): number {
  return Math.imul(rotateLeft(Math.imul(block, C1), 15), C2);
}

function finalMix(hash: number): number {
  let h = hash;
  h ^= h >>> 16;
  h = Math.imul(h, 0x85ebca6b);
  h ^= h >>> 13;
  h = Math.imul(h, 0xc2b2ae35);
  h ^= h >>> 16;
  return h;
}

export function murmur3x86_32(bytes: Uint8Array, seed = 0): number {
  const length = bytes.length;
  const blockEnd = length - (length & 3);
  let h = seed | 0;
  let index = 0;
  while (index < blockEnd) {
    const block =
      bytes[index] | (bytes[index + 1] << 8) | (bytes[index + 2] << 16) | (bytes[index + 3] << 24);
    h ^= mixBlock(block);
    h = rotateLeft(h, 13);
    h = (Math.imul(h, 5) + 0xe6546b64) | 0;
    index += 4;
  }
  const remaining = length & 3;
  if (remaining > 0) {
    let tail = bytes[blockEnd];
    if (remaining >= 2) {
      tail |= bytes[blockEnd + 1] << 8;
    }
    if (remaining === 3) {
      tail |= bytes[blockEnd + 2] << 16;
    }
    h ^= mixBlock(tail);
  }
  return finalMix(h ^ length) >>> 0;
}

export function murmur3x86_32OfText(text: string, seed = 0): number {
  return murmur3x86_32(encodeUtf8(text), seed);
}
