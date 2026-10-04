package dev.flagtide.domain.flag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.value.Percentage;
import dev.flagtide.domain.value.VariantKey;
import dev.flagtide.domain.value.Weight;
import java.util.List;
import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class RolloutPropertiesTest {

  private static List<RolloutEntry> entriesOf(List<Integer> weights) {
    return IntStream.range(0, weights.size())
        .mapToObj(
            index -> new RolloutEntry(new VariantKey("v" + index), Weight.of(weights.get(index))))
        .toList();
  }

  @Provide
  Arbitrary<List<Integer>> weightsSummingToTheTotal() {
    return Arbitraries.integers()
        .between(1, 8)
        .flatMap(
            size ->
                Arbitraries.integers()
                    .between(0, Weight.TOTAL)
                    .list()
                    .ofSize(size - 1)
                    .map(
                        cuts -> {
                          List<Integer> sorted = cuts.stream().sorted().toList();
                          return IntStream.rangeClosed(0, sorted.size())
                              .mapToObj(
                                  index -> {
                                    int upper =
                                        index == sorted.size() ? Weight.TOTAL : sorted.get(index);
                                    int lower = index == 0 ? 0 : sorted.get(index - 1);
                                    return upper - lower;
                                  })
                              .toList();
                        }));
  }

  @Provide
  Arbitrary<List<Integer>> weightsNotSummingToTheTotal() {
    return Combinators.combine(
            Arbitraries.integers().between(1, 6), Arbitraries.integers().between(0, 20_000))
        .as((size, each) -> IntStream.range(0, size).map(index -> each).boxed().toList())
        .filter(weights -> weights.stream().mapToInt(Integer::intValue).sum() != Weight.TOTAL);
  }

  @Property
  void weightsThatSumToTheTotalAreAcceptedAndKeepTheirSum(
      @ForAll("weightsSummingToTheTotal") List<Integer> weights) {
    Serving.Rollout rollout = new Serving.Rollout(entriesOf(weights));

    assertThat(rollout.entries().stream().mapToInt(entry -> entry.weight().units()).sum())
        .isEqualTo(Weight.TOTAL);
  }

  @Property
  void everyOtherSumIsRejected(@ForAll("weightsNotSummingToTheTotal") List<Integer> weights) {
    assertThatThrownBy(() -> new Serving.Rollout(entriesOf(weights)))
        .isInstanceOf(FlagtideException.class)
        .hasMessageContaining("sum to exactly 100000");
  }

  @Property
  void percentagesInsideTheBoundsAreAccepted(@ForAll @IntRange(min = 0, max = 100_000) int units) {
    Percentage percentage = Percentage.of(units);

    assertThat(percentage.units() + percentage.complement().units()).isEqualTo(Weight.TOTAL);
    assertThat(
            Serving.Rollout.split(new VariantKey("on"), new VariantKey("off"), percentage)
                .entries()
                .stream()
                .mapToInt(entry -> entry.weight().units())
                .sum())
        .isEqualTo(Weight.TOTAL);
  }

  @Property
  void percentagesOutsideTheBoundsAreRejected(@ForAll("outsideTheBounds") int units) {
    assertThatThrownBy(() -> Percentage.of(units)).isInstanceOf(FlagtideException.class);
    assertThatThrownBy(() -> Weight.of(units)).isInstanceOf(FlagtideException.class);
  }

  @Provide
  Arbitrary<Integer> outsideTheBounds() {
    return Arbitraries.oneOf(
        Arbitraries.integers().between(Integer.MIN_VALUE, -1),
        Arbitraries.integers().between(100_001, Integer.MAX_VALUE));
  }
}
