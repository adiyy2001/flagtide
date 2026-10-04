package dev.flagtide.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvaluatorTest {

  private static final Evaluator EVALUATOR = new Evaluator();
  private static final EvaluationContext CONTEXT = new EvaluationContext("u", Map.of());

  private static FlagConfig flag(Serve fallthrough, List<Rule> rules) {
    return new FlagConfig(
        "flag",
        FlagType.BOOLEAN,
        true,
        false,
        "a1b2c3",
        List.of(
            new Variant("on", new JsonValue.JsonBoolean(true)),
            new Variant("off", new JsonValue.JsonBoolean(false))),
        "off",
        rules,
        fallthrough);
  }

  @Test
  void servesTheFallthroughOfAFlagWithoutRules() {
    EvaluationResult result = EVALUATOR.evaluate(flag(new Serve.Single("on"), List.of()), CONTEXT);

    assertThat(result)
        .isEqualTo(
            new EvaluationResult(
                "on",
                new JsonValue.JsonBoolean(true),
                Reason.FALLTHROUGH,
                OptionalInt.empty(),
                Optional.empty(),
                OptionalInt.empty()));
  }

  @Test
  void rejectsARolloutThatDoesNotCoverTheBucket() {
    FlagConfig broken = flag(new Serve.Rollout(List.of(new WeightedVariant("on", 0))), List.of());

    assertThatThrownBy(() -> EVALUATOR.evaluate(broken, CONTEXT))
        .isInstanceOf(InvalidFlagConfigException.class)
        .hasMessageContaining("100000");
  }

  @Test
  void rejectsAServedVariantThatDoesNotExist() {
    FlagConfig broken = flag(new Serve.Single("ghost"), List.of());

    assertThatThrownBy(() -> EVALUATOR.evaluate(broken, CONTEXT))
        .isInstanceOf(InvalidFlagConfigException.class)
        .hasMessageContaining("ghost");
  }

  @Test
  void servesASegmentMemberThroughARule() {
    Rule rule =
        new Rule(
            "r", List.of(new Condition.SegmentMembership("beta", false)), new Serve.Single("on"));
    FlagConfig config = flag(new Serve.Single("off"), List.of(rule));
    Map<String, Segment> segments =
        Segment.indexByKey(List.of(new Segment("beta", Set.of("u"), Set.of(), List.of())));

    assertThat(EVALUATOR.evaluate(config, CONTEXT, segments).reason()).isEqualTo(Reason.RULE_MATCH);
    assertThat(
            EVALUATOR.evaluate(config, new EvaluationContext("other", Map.of()), segments).reason())
        .isEqualTo(Reason.FALLTHROUGH);
  }

  @Test
  void operatorNamesRoundTrip() {
    for (Operator operator : Operator.values()) {
      assertThat(Operator.fromWireName(operator.wireName())).contains(operator);
    }
    assertThat(Operator.fromWireName("nope")).isEmpty();
  }
}
