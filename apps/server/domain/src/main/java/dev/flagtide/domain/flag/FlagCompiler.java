package dev.flagtide.domain.flag;

import dev.flagtide.domain.evaluation.FlagConfig;
import dev.flagtide.domain.evaluation.Rule;
import dev.flagtide.domain.evaluation.Serve;
import dev.flagtide.domain.evaluation.Variant;
import dev.flagtide.domain.evaluation.WeightedVariant;
import dev.flagtide.domain.value.EnvironmentKey;

public final class FlagCompiler {

  private FlagCompiler() {}

  public static FlagConfig compile(Flag flag, EnvironmentKey environment) {
    FlagEnvironmentConfig config = flag.requireEnvironment(environment);
    return new FlagConfig(
        flag.key().value(),
        flag.type(),
        config.enabled(),
        config.killSwitch(),
        config.salt().value(),
        flag.variants().stream()
            .map(variant -> new Variant(variant.key().value(), variant.value()))
            .toList(),
        config.offVariant().value(),
        config.rules().stream().map(FlagCompiler::compileRule).toList(),
        compileServing(config.fallthrough()));
  }

  public static dev.flagtide.domain.evaluation.Segment compileSegment(
      dev.flagtide.domain.segment.Segment segment) {
    return new dev.flagtide.domain.evaluation.Segment(
        segment.key().value(), segment.included(), segment.excluded(), segment.rules());
  }

  private static Rule compileRule(TargetingRule rule) {
    return new Rule(rule.id().value(), rule.conditions(), compileServing(rule.serving()));
  }

  private static Serve compileServing(Serving serving) {
    return switch (serving) {
      case Serving.Fixed fixed -> new Serve.Single(fixed.variant().value());
      case Serving.Rollout rollout ->
          new Serve.Rollout(
              rollout.entries().stream()
                  .map(
                      entry -> new WeightedVariant(entry.variant().value(), entry.weight().units()))
                  .toList());
    };
  }
}
