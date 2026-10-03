package dev.flagwire.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class Murmur3X86x32Test {

  static Stream<Arguments> publishedBinaryVectors() {
    return Stream.of(
        Arguments.of("", 0, 0x00000000L),
        Arguments.of("", 1, 0x514E28B7L),
        Arguments.of("", 0xFFFFFFFF, 0x81F16F39L),
        Arguments.of("ffffffff", 0, 0x76293B50L),
        Arguments.of("21436587", 0, 0xF55B516BL),
        Arguments.of("21436587", 0x5082EDEE, 0x2362F9DEL),
        Arguments.of("214365", 0, 0x7E4A8634L),
        Arguments.of("2143", 0, 0xA0F7B07AL),
        Arguments.of("21", 0, 0x72661CF4L),
        Arguments.of("00000000", 0, 0x2362F9DEL),
        Arguments.of("000000", 0, 0x85F0B427L),
        Arguments.of("0000", 0, 0x30F4C306L),
        Arguments.of("00", 0, 0x514E28B7L));
  }

  static Stream<Arguments> publishedTextVectors() {
    return Stream.of(
        Arguments.of("test", 0, 0xBA6BD213L),
        Arguments.of("Hello, world!", 0, 0xC0363E43L),
        Arguments.of("The quick brown fox jumps over the lazy dog", 0, 0x2E4FF723L),
        Arguments.of("Hello, world!", 0x9747B28C, 0x24884CBAL),
        Arguments.of("The quick brown fox jumps over the lazy dog", 0x9747B28C, 0x2FA826CDL),
        Arguments.of("aaaa", 0x9747B28C, 0x5A97808AL),
        Arguments.of("aaa", 0x9747B28C, 0x283E0130L),
        Arguments.of("aa", 0x9747B28C, 0x5D211726L),
        Arguments.of("a", 0x9747B28C, 0x7FA09EA6L),
        Arguments.of("abcd", 0x9747B28C, 0xF0478627L),
        Arguments.of("abc", 0x9747B28C, 0xC84A62DDL),
        Arguments.of("ab", 0x9747B28C, 0x74875592L));
  }

  @ParameterizedTest
  @MethodSource("publishedBinaryVectors")
  void matchesPublishedBinaryVectors(String hex, int seed, long expected) {
    byte[] input = HexFormat.of().parseHex(hex);

    assertThat(Murmur3X86x32.hashUnsigned(input, seed)).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("publishedTextVectors")
  void matchesPublishedTextVectors(String text, int seed, long expected) {
    byte[] input = text.getBytes(StandardCharsets.UTF_8);

    assertThat(Murmur3X86x32.hashUnsigned(input, seed)).isEqualTo(expected);
  }

  @Test
  void reproducesTheSmhasherVerificationValue() {
    ByteBuffer collected = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
    for (int length = 0; length < 256; length++) {
      byte[] key = new byte[length];
      for (int index = 0; index < length; index++) {
        key[index] = (byte) index;
      }
      collected.putInt(Murmur3X86x32.hash(key, 256 - length));
    }

    assertThat(Murmur3X86x32.hashUnsigned(collected.array(), 0)).isEqualTo(0xB0F57EE3L);
  }

  @Test
  void signedAndUnsignedResultsAgree() {
    byte[] input = "test".getBytes(StandardCharsets.UTF_8);

    assertThat(Murmur3X86x32.hash(input, 0)).isEqualTo((int) 0xBA6BD213L);
    assertThat(Murmur3X86x32.hashUnsigned(input, 0)).isEqualTo(0xBA6BD213L);
  }
}
