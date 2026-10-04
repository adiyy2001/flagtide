package dev.flagtide.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagtide.domain.error.FlagtideException;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValueObjectsTest {

  private static final String SLUG_64 =
      "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String CHARS_65 =
      "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

  private static final List<Function<String, Object>> KEY_FACTORIES =
      List.of(ProjectKey::new, EnvironmentKey::new, FlagKey::new, SegmentKey::new, VariantKey::new);

  @ParameterizedTest
  @ValueSource(strings = {"a", "0", "new-checkout", "dark_mode", SLUG_64, "9lives"})
  void acceptsSlugs(String value) {
    KEY_FACTORIES.forEach(factory -> assertThat(factory.apply(value)).isNotNull());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "A", "-a", "_a", "has.dot", "has space", "ünicode", CHARS_65, "UPPER"})
  void rejectsStringsThatAreNotSlugs(String value) {
    KEY_FACTORIES.forEach(
        factory ->
            assertThatThrownBy(() -> factory.apply(value)).isInstanceOf(FlagtideException.class));
  }

  @Test
  void rejectsNullKeys() {
    assertThatThrownBy(() -> new FlagKey(null)).isInstanceOf(FlagtideException.class);
  }

  @Test
  void ordersAndPrintsKeysByValue() {
    assertThat(new FlagKey("a")).isLessThan(new FlagKey("b"));
    assertThat(new FlagKey("a")).hasToString("a");
    assertThat(ProjectKey.of("p")).isEqualTo(new ProjectKey("p"));
    assertThat(new EnvironmentKey("x")).isLessThan(new EnvironmentKey("y"));
    assertThat(new SegmentKey("x")).isLessThan(new SegmentKey("y"));
    assertThat(new VariantKey("x")).isLessThan(new VariantKey("y")).hasToString("x");
    assertThat(new ProjectKey("x")).isLessThan(new ProjectKey("y")).hasToString("x");
  }

  @Test
  void acceptsRuleIdsOfReasonableLength() {
    assertThat(RuleId.of("beta users").value()).isEqualTo("beta users");
    assertThatThrownBy(() -> RuleId.of(" ")).isInstanceOf(FlagtideException.class);
    assertThatThrownBy(() -> RuleId.of("x".repeat(65))).isInstanceOf(FlagtideException.class);
    assertThatThrownBy(() -> RuleId.of(null)).isInstanceOf(FlagtideException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "a1", "deadbeef", "0123456789abcdef"})
  void acceptsHexSalts(String value) {
    assertThat(Salt.of(value).value()).isEqualTo(value);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "A1", "xyz", "a.b", CHARS_65})
  void rejectsOtherSalts(String value) {
    assertThatThrownBy(() -> Salt.of(value)).isInstanceOf(FlagtideException.class);
  }

  @Test
  void rejectsANullSalt() {
    assertThatThrownBy(() -> Salt.of(null)).isInstanceOf(FlagtideException.class);
  }

  @Test
  void keepsWeightsAndPercentagesWithinZeroAndOneHundredThousand() {
    assertThat(Weight.of(0).units()).isZero();
    assertThat(Weight.of(100_000).units()).isEqualTo(100_000);
    assertThatThrownBy(() -> Weight.of(-1)).isInstanceOf(FlagtideException.class);
    assertThatThrownBy(() -> Weight.of(100_001)).isInstanceOf(FlagtideException.class);
    assertThatThrownBy(() -> Percentage.of(-1)).isInstanceOf(FlagtideException.class);
    assertThatThrownBy(() -> Percentage.of(100_001)).isInstanceOf(FlagtideException.class);
  }

  @Test
  void convertsPercentagesToWeightsAndPercent() {
    Percentage quarter = Percentage.of(25_000);

    assertThat(quarter.complement()).isEqualTo(Percentage.of(75_000));
    assertThat(quarter.toWeight()).isEqualTo(Weight.of(25_000));
    assertThat(quarter.asPercent()).isEqualTo(25.0);
    assertThat(Percentage.of(1).asPercent()).isEqualTo(0.001);
  }

  @Test
  void countsRevisionsUpFromTheFirstOne() {
    assertThat(Revision.NONE.next()).isEqualTo(Revision.FIRST);
    assertThat(Revision.of(4).next()).isEqualTo(Revision.of(5));
    assertThat(Revision.of(4)).isGreaterThan(Revision.of(3));
    assertThatThrownBy(() -> Revision.of(-1)).isInstanceOf(FlagtideException.class);
  }

  @Test
  void countsEnvironmentVersionsUpAndComparesThem() {
    assertThat(EnvironmentVersion.ZERO.next()).isEqualTo(EnvironmentVersion.of(1));
    assertThat(EnvironmentVersion.of(5).isAfter(EnvironmentVersion.of(4))).isTrue();
    assertThat(EnvironmentVersion.of(5).isAfter(EnvironmentVersion.of(5))).isFalse();
    assertThat(EnvironmentVersion.of(5)).isGreaterThan(EnvironmentVersion.of(4));
    assertThatThrownBy(() -> EnvironmentVersion.of(-1)).isInstanceOf(FlagtideException.class);
  }

  @Test
  void pairsAProjectWithAnEnvironment() {
    EnvironmentRef ref = EnvironmentRef.of(ProjectKey.of("p"), EnvironmentKey.of("e"));

    assertThat(ref.project()).isEqualTo(ProjectKey.of("p"));
    assertThat(ref.environment()).isEqualTo(EnvironmentKey.of("e"));
  }
}
