package dev.flagtide.domain.flag;

import static dev.flagtide.domain.flag.FlagFixtures.DEV;
import static dev.flagtide.domain.flag.FlagFixtures.OFF;
import static dev.flagtide.domain.flag.FlagFixtures.ON;
import static dev.flagtide.domain.flag.FlagFixtures.PROD;
import static dev.flagtide.domain.flag.FlagFixtures.booleanFlag;
import static dev.flagtide.domain.flag.FlagFixtures.stamp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.AttributeValue;
import dev.flagtide.domain.evaluation.Condition;
import dev.flagtide.domain.evaluation.FlagConfig;
import dev.flagtide.domain.evaluation.FlagType;
import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.evaluation.Operator;
import dev.flagtide.domain.evaluation.Rule;
import dev.flagtide.domain.evaluation.Serve;
import dev.flagtide.domain.evaluation.Variant;
import dev.flagtide.domain.evaluation.WeightedVariant;
import dev.flagtide.domain.segment.Segment;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.Percentage;
import dev.flagtide.domain.value.RuleId;
import dev.flagtide.domain.value.SegmentKey;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FlagCompilerTest {

  @Test
  void compilesTheClientFormatOfOneEnvironment() {
    Condition proPlan =
        new Condition.Attribute(
            "plan", Operator.EQUALS, List.of(new AttributeValue.TextValue("pro")), false);
    Condition inBeta = new Condition.SegmentMembership("beta", true);
    Flag flag =
        booleanFlag("checkout")
            .configure(
                PROD,
                new EnvironmentSettings(
                    true,
                    OFF,
                    List.of(
                        new TargetingRule(
                            RuleId.of("pro-users"),
                            0,
                            List.of(proPlan, inBeta),
                            Serving.Rollout.split(ON, OFF, Percentage.of(25_000)))),
                    Serving.fixed(OFF)),
                Set.of(new SegmentKey("beta")),
                stamp("a"))
            .next();

    FlagConfig config = FlagCompiler.compile(flag, PROD);

    assertThat(config)
        .isEqualTo(
            new FlagConfig(
                "checkout",
                FlagType.BOOLEAN,
                true,
                false,
                "c3",
                List.of(
                    new Variant("on", new JsonValue.JsonBoolean(true)),
                    new Variant("off", new JsonValue.JsonBoolean(false))),
                "off",
                List.of(
                    new Rule(
                        "pro-users",
                        List.of(proPlan, inBeta),
                        new Serve.Rollout(
                            List.of(
                                new WeightedVariant("on", 25_000),
                                new WeightedVariant("off", 75_000))))),
                new Serve.Single("off")));
  }

  @Test
  void compilesThePerEnvironmentSaltAndKillSwitch() {
    Flag flag = booleanFlag("checkout").engageKillSwitch(DEV, stamp("a")).next();

    assertThat(FlagCompiler.compile(flag, DEV).salt()).isEqualTo("a1");
    assertThat(FlagCompiler.compile(flag, DEV).killSwitch()).isTrue();
    assertThat(FlagCompiler.compile(flag, PROD).salt()).isEqualTo("c3");
    assertThat(FlagCompiler.compile(flag, PROD).killSwitch()).isFalse();
  }

  @Test
  void failsForAnEnvironmentTheFlagDoesNotHave() {
    assertThatThrownBy(() -> FlagCompiler.compile(booleanFlag("f"), new EnvironmentKey("none")))
        .isInstanceOf(FlagtideException.class);
  }

  @Test
  void compilesSegmentsToTheClientFormat() {
    Segment segment =
        Segment.create(
                DEV,
                new SegmentKey("beta"),
                "Beta",
                Set.of("u1"),
                Set.of("u2"),
                List.of(),
                stamp("a"))
            .next();

    assertThat(FlagCompiler.compileSegment(segment))
        .isEqualTo(
            new dev.flagtide.domain.evaluation.Segment(
                "beta", Set.of("u1"), Set.of("u2"), List.of()));
  }
}
