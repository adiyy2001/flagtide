package dev.flagtide.domain.flag;

import static dev.flagtide.domain.flag.FlagFixtures.DEV;
import static dev.flagtide.domain.flag.FlagFixtures.OFF;
import static dev.flagtide.domain.flag.FlagFixtures.ON;
import static dev.flagtide.domain.flag.FlagFixtures.booleanFlag;
import static dev.flagtide.domain.flag.FlagFixtures.stamp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagtide.domain.evaluation.AttributeValue;
import dev.flagtide.domain.evaluation.Condition;
import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.evaluation.Operator;
import dev.flagtide.domain.json.Json;
import dev.flagtide.domain.json.JsonFields;
import dev.flagtide.domain.value.Percentage;
import dev.flagtide.domain.value.RuleId;
import dev.flagtide.domain.value.SegmentKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FlagDocumentTest {

  private static Flag richFlag() {
    return booleanFlag("checkout")
        .configure(
            DEV,
            new EnvironmentSettings(
                true,
                OFF,
                List.of(
                    new TargetingRule(
                        RuleId.of("beta"),
                        0,
                        List.of(new Condition.SegmentMembership("beta", false)),
                        Serving.fixed(ON)),
                    new TargetingRule(
                        RuleId.of("ramp"),
                        1,
                        List.of(
                            new Condition.Attribute(
                                "age",
                                Operator.GTE,
                                List.of(new AttributeValue.NumberValue(18)),
                                false)),
                        Serving.Rollout.split(ON, OFF, Percentage.of(10_000)))),
                Serving.fixed(OFF)),
            Set.of(new SegmentKey("beta")),
            stamp("a"))
        .next();
  }

  @Test
  void roundTripsAFlagThroughItsDocument() {
    Flag flag = richFlag();

    assertThat(FlagDocument.fromJson(FlagDocument.toJson(flag))).isEqualTo(flag);
  }

  @Test
  void describesTheDefinitionWithoutTheEnvironments() {
    JsonFields definition = JsonFields.of("d", FlagDocument.definitionToJson(richFlag()));

    assertThat(definition.text("key")).isEqualTo("checkout");
    assertThat(definition.text("type")).isEqualTo("boolean");
    assertThat(definition.array("variants")).hasSize(2);
    assertThat(definition.find("environments")).isEmpty();
  }

  @Test
  void describesOneEnvironmentConfig() {
    JsonFields config =
        JsonFields.of("c", FlagDocument.environmentToJson(richFlag().requireEnvironment(DEV)));

    assertThat(config.bool("enabled")).isTrue();
    assertThat(config.text("offVariant")).isEqualTo("off");
    assertThat(config.array("rules")).hasSize(2);
  }

  @Test
  void rejectsAnUnknownFlagType() {
    JsonFields original = JsonFields.of("f", FlagDocument.toJson(richFlag()));
    JsonValue.JsonObject tampered =
        new JsonValue.JsonObject(
            Stream.concat(
                    original.members().entrySet().stream()
                        .filter(entry -> !entry.getKey().equals("type")),
                    Stream.of(Map.entry("type", Json.text("float"))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

    assertThatThrownBy(() -> FlagDocument.fromJson(tampered))
        .hasMessageContaining("unknown flag type float");
  }
}
