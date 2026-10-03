package dev.flagwire.domain.flag;

import dev.flagwire.domain.evaluation.FlagConfig;
import dev.flagwire.domain.evaluation.Rule;
import dev.flagwire.domain.evaluation.Serve;
import dev.flagwire.domain.evaluation.Variant;
import dev.flagwire.domain.evaluation.WeightedVariant;
import dev.flagwire.domain.value.EnvironmentKey;

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

  public static dev.flagwire.domain.evaluation.Segment compileSegment(
      dev.flagwire.domain.segment.Segment segment) {
    return new dev.flagwire.domain.evaluation.Segment(
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
