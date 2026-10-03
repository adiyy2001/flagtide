package dev.flagwire.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;

class Utf8Test {

  @Property
  void agreesWithTheJdkOnWellFormedStrings(@ForAll @StringLength(max = 64) String text) {
    assertThat(Utf8.encode(text)).isEqualTo(text.getBytes(StandardCharsets.UTF_8));
    assertThat(Utf8.length(text)).isEqualTo(text.getBytes(StandardCharsets.UTF_8).length);
  }

  @Test
  void encodesTheBoundariesOfEverySequenceLength() {
    assertThat(Utf8.encode("\u007f")).containsExactly(0x7F);
    assertThat(Utf8.encode("\u0080")).containsExactly(0xC2, 0x80);
    assertThat(Utf8.encode("߿")).containsExactly(0xDF, 0xBF);
    assertThat(Utf8.encode("ࠀ")).containsExactly(0xE0, 0xA0, 0x80);
    assertThat(Utf8.encode("￿")).containsExactly(0xEF, 0xBF, 0xBF);
    assertThat(Utf8.encode(new String(Character.toChars(0x10000))))
        .containsExactly(0xF0, 0x90, 0x80, 0x80);
    assertThat(Utf8.encode(new String(Character.toChars(0x10FFFF))))
        .containsExactly(0xF4, 0x8F, 0xBF, 0xBF);
  }

  @Test
  void writesOneReplacementCharacterPerLoneSurrogate() {
    byte[] replacement = {(byte) 0xEF, (byte) 0xBF, (byte) 0xBD};

    assertThat(Utf8.encode("\ud800")).isEqualTo(replacement);
    assertThat(Utf8.encode("\udc00")).isEqualTo(replacement);
    assertThat(Utf8.encode("\ude00\ud83d")).hasSize(6);
    assertThat(Utf8.encode("a\ud800b")).containsExactly(0x61, 0xEF, 0xBF, 0xBD, 0x62);
    assertThat(Utf8.length("\ud800")).isEqualTo(3);
  }

  @Test
  void theJdkEncoderWouldWriteAQuestionMarkInstead() {
    assertThat("\ud800".getBytes(StandardCharsets.UTF_8)).containsExactly('?');
  }
}
