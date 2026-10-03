package dev.flagwire.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BucketingTest {

  @Test
  void hashesFlagKeySaltAndContextKeyJoinedByDots() {
    long hash =
        Murmur3X86x32.hashUnsigned(
            "new-checkout.9f2c41.user-42".getBytes(StandardCharsets.UTF_8), 0);

    assertThat(Bucketing.bucket("new-checkout", "9f2c41", "user-42"))
        .isEqualTo((int) (hash % 100_000));
  }

  @Test
  void staysInsideTheBucketSpace() {
    for (int index = 0; index < 5000; index++) {
      assertThat(Bucketing.bucket("flag", "a1", "key-" + index))
          .isBetween(0, Bucketing.BUCKET_SPACE - 1);
    }
  }

  @Test
  void reactsToEveryPartOfTheInput() {
    int base = Bucketing.bucket("flag", "a1", "user");

    assertThat(Bucketing.bucket("flag2", "a1", "user")).isNotEqualTo(base);
    assertThat(Bucketing.bucket("flag", "a2", "user")).isNotEqualTo(base);
    assertThat(Bucketing.bucket("flag", "a1", "user2")).isNotEqualTo(base);
  }

  @Test
  void hashesALoneSurrogateLikeTheReplacementCharacter() {
    assertThat(Bucketing.bucket("flag", "a1", "\ud800"))
        .isEqualTo(Bucketing.bucket("flag", "a1", "�"));
  }

  @Test
  void doesNotNormalizeUnicode() {
    assertThat(Bucketing.bucket("flag", "a1", "café"))
        .isNotEqualTo(Bucketing.bucket("flag", "a1", "café"));
  }
}
