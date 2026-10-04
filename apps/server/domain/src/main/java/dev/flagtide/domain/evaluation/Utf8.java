package dev.flagtide.domain.evaluation;

public final class Utf8 {

  private static final int REPLACEMENT_CHARACTER = 0xFFFD;

  private Utf8() {}

  public static byte[] encode(String text) {
    byte[] bytes = new byte[length(text)];
    int offset = 0;
    int index = 0;
    while (index < text.length()) {
      int codePoint = codePointAt(text, index);
      index += codePoint >= 0x10000 ? 2 : 1;
      offset = write(bytes, offset, codePoint);
    }
    return bytes;
  }

  public static int length(String text) {
    int length = 0;
    int index = 0;
    while (index < text.length()) {
      int codePoint = codePointAt(text, index);
      index += codePoint >= 0x10000 ? 2 : 1;
      length += encodedLength(codePoint);
    }
    return length;
  }

  private static int codePointAt(String text, int index) {
    char unit = text.charAt(index);
    if (Character.isHighSurrogate(unit)) {
      boolean paired =
          index + 1 < text.length() && Character.isLowSurrogate(text.charAt(index + 1));
      return paired ? Character.toCodePoint(unit, text.charAt(index + 1)) : REPLACEMENT_CHARACTER;
    }
    return Character.isLowSurrogate(unit) ? REPLACEMENT_CHARACTER : unit;
  }

  private static int encodedLength(int codePoint) {
    if (codePoint < 0x80) {
      return 1;
    }
    if (codePoint < 0x800) {
      return 2;
    }
    return codePoint < 0x10000 ? 3 : 4;
  }

  private static int write(byte[] bytes, int offset, int codePoint) {
    int next = offset;
    if (codePoint < 0x80) {
      bytes[next++] = (byte) codePoint;
    } else if (codePoint < 0x800) {
      bytes[next++] = (byte) (0xC0 | (codePoint >> 6));
      bytes[next++] = (byte) (0x80 | (codePoint & 0x3F));
    } else if (codePoint < 0x10000) {
      bytes[next++] = (byte) (0xE0 | (codePoint >> 12));
      bytes[next++] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
      bytes[next++] = (byte) (0x80 | (codePoint & 0x3F));
    } else {
      bytes[next++] = (byte) (0xF0 | (codePoint >> 18));
      bytes[next++] = (byte) (0x80 | ((codePoint >> 12) & 0x3F));
      bytes[next++] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
      bytes[next++] = (byte) (0x80 | (codePoint & 0x3F));
    }
    return next;
  }
}
