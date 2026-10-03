package dev.flagwire.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SemanticVersionTest {

  private static int compare(String left, String right) {
    return Integer.signum(
        SemanticVersion.parse(left)
            .orElseThrow()
            .compareTo(SemanticVersion.parse(right).orElseThrow()));
  }

  @Test
  void splitsTheParts() {
    assertThat(SemanticVersion.parse("1.2.3-alpha.1+build.5"))
        .contains(new SemanticVersion("1", "2", "3", List.of("alpha", "1")));
  }

  @Test
  void keepsHugeNumbersAsDigitStrings() {
    assertThat(SemanticVersion.parse("99999999999999999999.0.0").orElseThrow().major())
        .isEqualTo("99999999999999999999");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "1",
        "1.2",
        "v1.2.3",
        "01.2.3",
        "1.2.3-01",
        "1.2.3-",
        "1.2.3+",
        " 1.2.3",
        "1.2.3\n",
        "１.2.3"
      })
  void rejectsInvalidVersions(String text) {
    assertThat(SemanticVersion.parse(text)).isEmpty();
  }

  @Test
  void ordersThePrecedenceExampleOfSemverOrg() {
    List<String> ordered =
        List.of(
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-alpha.beta",
            "1.0.0-beta",
            "1.0.0-beta.2",
            "1.0.0-beta.11",
            "1.0.0-rc.1",
            "1.0.0");
    for (int index = 0; index + 1 < ordered.size(); index++) {
      assertThat(compare(ordered.get(index), ordered.get(index + 1))).isEqualTo(-1);
      assertThat(compare(ordered.get(index + 1), ordered.get(index))).isEqualTo(1);
    }
  }

  @Test
  void comparesNumbersBeyondTwoToTheFiftyThirdExactly() {
    assertThat(compare("9007199254740993.0.0", "9007199254740992.0.0")).isEqualTo(1);
    assertThat(compare("1.0.0-18446744073709551616", "1.0.0-18446744073709551615")).isEqualTo(1);
  }

  @Test
  void ignoresBuildMetadata() {
    assertThat(compare("1.0.0+a", "1.0.0+b")).isZero();
  }

  @Test
  void ordersNumericIdentifiersBelowAlphanumericOnes() {
    assertThat(compare("1.0.0-99", "1.0.0-a")).isEqualTo(-1);
    assertThat(compare("1.0.0-A", "1.0.0-a")).isEqualTo(-1);
  }
}
