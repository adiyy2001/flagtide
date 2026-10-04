package dev.flagtide.adapter.out.memory.usecase;

import dev.flagtide.application.usecase.CreateFlag;
import dev.flagtide.application.usecase.SaveSegment;
import dev.flagtide.domain.evaluation.AttributeValue;
import dev.flagtide.domain.evaluation.Condition;
import dev.flagtide.domain.evaluation.FlagType;
import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.evaluation.Operator;
import dev.flagtide.domain.flag.EnvironmentSettings;
import dev.flagtide.domain.flag.Flag;
import dev.flagtide.domain.flag.FlagVariant;
import dev.flagtide.domain.flag.Serving;
import dev.flagtide.domain.flag.TargetingRule;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.Percentage;
import dev.flagtide.domain.value.RuleId;
import dev.flagtide.domain.value.SegmentKey;
import dev.flagtide.domain.value.VariantKey;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class Fixtures {

  static final VariantKey ON = new VariantKey("on");
  static final VariantKey OFF = new VariantKey("off");

  private Fixtures() {}

  static List<FlagVariant> booleanVariants() {
    return List.of(
        new FlagVariant(ON, new JsonValue.JsonBoolean(true)),
        new FlagVariant(OFF, new JsonValue.JsonBoolean(false)));
  }

  static CreateFlag.Command booleanFlag(String key) {
    return new CreateFlag.Command(
        new FlagKey(key), "the " + key + " flag", FlagType.BOOLEAN, booleanVariants(), OFF, OFF);
  }

  static Flag createFlag(Flagtide app, String key) {
    return app.createFlag.execute(app.admin(Flagtide.DEV), booleanFlag(key)).value();
  }

  static Condition.Attribute attribute(String name, Operator operator, String... values) {
    return new Condition.Attribute(
        name,
        operator,
        Arrays.stream(values).<AttributeValue.Scalar>map(AttributeValue.TextValue::new).toList(),
        false);
  }

  static SaveSegment.Command segment(EnvironmentKey environment, String key, String... included) {
    return new SaveSegment.Command(
        environment,
        new SegmentKey(key),
        Optional.empty(),
        "Segment " + key,
        Set.of(included),
        Set.of(),
        List.of());
  }

  static SaveSegment.Command betaTesters(EnvironmentKey environment) {
    return new SaveSegment.Command(
        environment,
        new SegmentKey("beta"),
        Optional.empty(),
        "Beta testers",
        Set.of(),
        Set.of(),
        List.of(List.of(attribute("plan", Operator.EQUALS, "beta"))));
  }

  static EnvironmentSettings rolloutBehindSegment(
      String segment, int percentOfUnits, boolean enabled) {
    TargetingRule rule =
        new TargetingRule(
            new RuleId("beta-rollout"),
            0,
            List.of(new Condition.SegmentMembership(segment, false)),
            Serving.Rollout.split(ON, OFF, Percentage.of(percentOfUnits)));
    return new EnvironmentSettings(enabled, OFF, List.of(rule), Serving.fixed(OFF));
  }
}
