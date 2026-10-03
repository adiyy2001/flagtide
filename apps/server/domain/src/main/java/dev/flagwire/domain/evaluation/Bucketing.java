package dev.flagwire.domain.evaluation;

public final class Bucketing {

  public static final int BUCKET_SPACE = 100_000;

  private Bucketing() {}

  public static int bucket(String flagKey, String salt, String contextKey) {
    byte[] input = Utf8.encode(flagKey + "." + salt + "." + contextKey);
    return (int) (Murmur3X86x32.hashUnsigned(input, 0) % BUCKET_SPACE);
  }
}
