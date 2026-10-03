package dev.flagwire.domain.flag;

import static dev.flagwire.domain.flag.FlagFixtures.DEV;
import static dev.flagwire.domain.flag.FlagFixtures.stamp;
import static org.assertj.core.api.Assertions.assertThat;

import dev.flagwire.domain.evaluation.AttributeValue;
import dev.flagwire.domain.evaluation.Bucketing;
import dev.flagwire.domain.evaluation.Condition;
import dev.flagwire.domain.evaluation.EvaluationContext;
import dev.flagwire.domain.evaluation.Evaluator;
import dev.flagwire.domain.evaluation.FlagType;
import dev.flagwire.domain.evaluation.JsonValue;
import dev.flagwire.domain.evaluation.Operator;
import dev.flagwire.domain.value.FlagKey;
import dev.flagwire.domain.value.RuleId;
import dev.flagwire.domain.value.VariantKey;
import dev.flagwire.domain.value.Weight;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class CompiledEvaluationPropertiesTest {

  private static final List<String> PLANS = List.of("free", "pro", "team");
  private static final List<VariantKey> KEYS =
      List.of(new VariantKey("a"), new VariantKey("b"), new VariantKey("c"));
  private static final Evaluator EVALUATOR = new Evaluator();

  record RuleDraft(Condition condition, Serving serving) {}

  record Scenario(Flag flag, String contextKey, Optional<String> plan) {}

  private static Flag stringFlag() {
    return Flag.create(
            new FlagKey("generated"),
            "",
            FlagType.STRING,
            KEYS.stream()
                .map(key -> new FlagVariant(key, new JsonValue.JsonString(key.value())))
                .toList(),
            KEYS.get(0),
            KEYS.get(0),
            FlagFixtures.salts(),
            stamp("gen"))
        .next();
  }

  private static Arbitrary<Serving> servings() {
    Arbitrary<Serving> fixed = Arbitraries.of(KEYS).map(Serving::fixed);
    Arbitrary<Serving> rollout =
        Arbitraries.integers()
            .between(0, Weight.TOTAL)
            .tuple2()
            .map(
                cuts -> {
                  int low = Math.min(cuts.get1(), cuts.get2());
                  int high = Math.max(cuts.get1(), cuts.get2());
                  return new Serving.Rollout(
                      List.of(
                          new RolloutEntry(KEYS.get(0), Weight.of(low)),
                          new RolloutEntry(KEYS.get(1), Weight.of(high - low)),
                          new RolloutEntry(KEYS.get(2), Weight.of(Weight.TOTAL - high))));
                });
    return Arbitraries.oneOf(fixed, rollout);
  }

  private static Arbitrary<Condition> conditions() {
    return Arbitraries.of(PLANS)
        .set()
        .ofMinSize(1)
        .ofMaxSize(3)
        .map(
            plans ->
                new Condition.Attribute(
                    "plan",
                    Operator.IN,
                    plans.stream()
                        .sorted()
                        .<AttributeValue.Scalar>map(AttributeValue.TextValue::new)
                        .toList(),
                    false));
  }

  @Provide
  Arbitrary<Scenario> scenarios() {
    Arbitrary<List<TargetingRule>> rules =
        Combinators.combine(servings(), conditions())
            .as((serving, condition) -> new RuleDraft(condition, serving))
            .list()
            .ofMaxSize(4)
            .map(
                pairs ->
                    IntStream.range(0, pairs.size())
                        .mapToObj(
                            index ->
                                new TargetingRule(
                                    RuleId.of("rule-" + index),
                                    index,
                                    List.of(pairs.get(index).condition()),
                                    pairs.get(index).serving()))
                        .toList());
    return Combinators.combine(
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
            rules,
            servings(),
            Arbitraries.strings().ofMaxLength(12),
            Arbitraries.of(PLANS).optional())
        .as(
            (enabled, kill, ruleList, fallthrough, contextKey, plan) -> {
              Flag configured =
                  stringFlag()
                      .configure(
                          DEV,
                          new EnvironmentSettings(enabled, KEYS.get(2), ruleList, fallthrough),
                          Set.of(),
                          stamp("gen"))
                      .next();
              Flag flag = kill ? configured.engageKillSwitch(DEV, stamp("gen")).next() : configured;
              return new Scenario(flag, contextKey, plan);
            });
  }

  private static VariantKey direct(Scenario scenario) {
    FlagEnvironmentConfig config = scenario.flag().requireEnvironment(DEV);
    if (config.killSwitch() || !config.enabled()) {
      return config.offVariant();
    }
    Serving chosen =
        config.rules().stream()
            .filter(rule -> matches(rule, scenario.plan()))
            .map(TargetingRule::serving)
            .findFirst()
            .orElse(config.fallthrough());
    return switch (chosen) {
      case Serving.Fixed fixed -> fixed.variant();
      case Serving.Rollout rollout ->
          pick(
              rollout,
              Bucketing.bucket(
                  scenario.flag().key().value(), config.salt().value(), scenario.contextKey()));
    };
  }

  private static boolean matches(TargetingRule rule, Optional<String> plan) {
    return rule.conditions().stream()
        .map(Condition.Attribute.class::cast)
        .allMatch(
            condition ->
                plan.map(value -> condition.values().contains(new AttributeValue.TextValue(value)))
                    .orElse(false));
  }

  private static VariantKey pick(Serving.Rollout rollout, int bucket) {
    int running = 0;
    for (RolloutEntry entry : rollout.entries()) {
      running += entry.weight().units();
      if (running > bucket) {
        return entry.variant();
      }
    }
    throw new IllegalStateException("weights do not cover the bucket");
  }

  @Property
  void compilingThenEvaluatingMatchesEvaluatingTheDomainObjects(
      @ForAll("scenarios") Scenario scenario) {
    Map<String, JsonValue> attributes =
        scenario
            .plan()
            .map(plan -> Map.<String, JsonValue>of("plan", new JsonValue.JsonString(plan)))
            .orElse(Map.of());

    String viaCompiler =
        EVALUATOR
            .evaluate(
                FlagCompiler.compile(scenario.flag(), DEV),
                EvaluationContext.of(scenario.contextKey(), attributes))
            .variantKey();

    assertThat(viaCompiler).isEqualTo(direct(scenario).value());
  }

  @Property
  void aKillSwitchAlwaysServesTheOffVariant(@ForAll("scenarios") Scenario scenario) {
    Flag killed = scenario.flag().engageKillSwitch(DEV, stamp("gen")).next();

    String served =
        EVALUATOR
            .evaluate(
                FlagCompiler.compile(killed, DEV),
                EvaluationContext.of(scenario.contextKey(), Map.of()))
            .variantKey();

    assertThat(served).isEqualTo(killed.requireEnvironment(DEV).offVariant().value());
  }

  @Property
  void everyGeneratedFlagSurvivesTheDocumentRoundTrip(@ForAll("scenarios") Scenario scenario) {
    assertThat(FlagDocument.fromJson(FlagDocument.toJson(scenario.flag())))
        .isEqualTo(scenario.flag());
  }
}
