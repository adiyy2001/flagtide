package dev.flagwire.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class BucketingPropertiesTest {

  private static final Evaluator EVALUATOR = new Evaluator();
  private static final int BINS = 100;
  private static final int KEYS = 1_000_000;
  private static final double Z_FOR_ALPHA_ONE_IN_TEN_THOUSAND = 3.719;

  @Provide
  Arbitrary<String> hexSalts() {
    return Arbitraries.strings().withChars("0123456789abcdef").ofMinLength(1).ofMaxLength(16);
  }

  @Provide
  Arbitrary<String> contextKeys() {
    return Arbitraries.strings().all().ofMaxLength(24);
  }

  static double chiSquareCriticalValue(int degreesOfFreedom) {
    double shape = 2.0 / (9.0 * degreesOfFreedom);
    return degreesOfFreedom
        * Math.pow(1 - shape + Z_FOR_ALPHA_ONE_IN_TEN_THOUSAND * Math.sqrt(shape), 3);
  }

  static FlagConfig booleanRollout(String salt, int onWeight) {
    return new FlagConfig(
        "rollout",
        FlagType.BOOLEAN,
        true,
        false,
        salt,
        List.of(
            new Variant("on", new JsonValue.JsonBoolean(true)),
            new Variant("off", new JsonValue.JsonBoolean(false))),
        "off",
        List.of(),
        new Serve.Rollout(
            List.of(
                new WeightedVariant("on", onWeight),
                new WeightedVariant("off", 100_000 - onWeight))));
  }

  @Property(tries = 3, seed = "20261003")
  void bucketsAreUniformOverOneMillionKeys(@ForAll("hexSalts") String salt, @ForAll long keySeed) {
    SplittableRandom random = new SplittableRandom(keySeed);
    int[] counts = new int[BINS];
    for (int index = 0; index < KEYS; index++) {
      String key = Long.toString(random.nextLong(), 36);
      counts[Bucketing.bucket("uniformity", salt, key) / (Bucketing.BUCKET_SPACE / BINS)]++;
    }
    double expected = (double) KEYS / BINS;
    double statistic = 0;
    for (int count : counts) {
      statistic += (count - expected) * (count - expected) / expected;
    }

    assertThat(statistic).isLessThan(chiSquareCriticalValue(BINS - 1));
  }

  @Property
  void theSameContextAlwaysGetsTheSameResult(
      @ForAll("hexSalts") String salt,
      @ForAll("contextKeys") String key,
      @ForAll @IntRange(min = 0, max = 100_000) int onWeight) {
    FlagConfig flag = booleanRollout(salt, onWeight);
    EvaluationContext context = new EvaluationContext(key, Map.of());

    assertThat(EVALUATOR.evaluate(flag, context)).isEqualTo(EVALUATOR.evaluate(flag, context));
  }

  @Property
  void raisingABooleanRolloutNeverDropsAContext(
      @ForAll("hexSalts") String salt,
      @ForAll("contextKeys") String key,
      @ForAll @IntRange(min = 0, max = 100_000) int first,
      @ForAll @IntRange(min = 0, max = 100_000) int second) {
    int lower = Math.min(first, second);
    int higher = Math.max(first, second);
    EvaluationContext context = new EvaluationContext(key, Map.of());

    boolean inAtLower =
        EVALUATOR.evaluate(booleanRollout(salt, lower), context).variantKey().equals("on");
    boolean inAtHigher =
        EVALUATOR.evaluate(booleanRollout(salt, higher), context).variantKey().equals("on");

    assertThat(!inAtLower || inAtHigher).isTrue();
  }
}
