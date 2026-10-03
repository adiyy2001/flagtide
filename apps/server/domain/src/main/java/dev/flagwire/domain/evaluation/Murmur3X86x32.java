package dev.flagwire.domain.evaluation;

public final class Murmur3X86x32 {

  private static final int C1 = 0xCC9E2D51;
  private static final int C2 = 0x1B873593;

  private Murmur3X86x32() {}

  public static int hash(byte[] data, int seed) {
    int length = data.length;
    int blockEnd = length & ~3;
    int h = seed;
    for (int index = 0; index < blockEnd; index += 4) {
      int block =
          (data[index] & 0xFF)
              | ((data[index + 1] & 0xFF) << 8)
              | ((data[index + 2] & 0xFF) << 16)
              | ((data[index + 3] & 0xFF) << 24);
      h ^= mixBlock(block);
      h = Integer.rotateLeft(h, 13);
      h = h * 5 + 0xE6546B64;
    }
    int remaining = length & 3;
    if (remaining > 0) {
      int tail = data[blockEnd] & 0xFF;
      if (remaining >= 2) {
        tail |= (data[blockEnd + 1] & 0xFF) << 8;
      }
      if (remaining == 3) {
        tail |= (data[blockEnd + 2] & 0xFF) << 16;
      }
      h ^= mixBlock(tail);
    }
    return finalMix(h ^ length);
  }

  public static long hashUnsigned(byte[] data, int seed) {
    return Integer.toUnsignedLong(hash(data, seed));
  }

  private static int mixBlock(int block) {
    return Integer.rotateLeft(block * C1, 15) * C2;
  }

  private static int finalMix(int hash) {
    int h = hash;
    h ^= h >>> 16;
    h *= 0x85EBCA6B;
    h ^= h >>> 13;
    h *= 0xC2B2AE35;
    h ^= h >>> 16;
    return h;
  }
}
