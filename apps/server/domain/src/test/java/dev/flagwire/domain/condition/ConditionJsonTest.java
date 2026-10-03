package dev.flagwire.domain.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.evaluation.AttributeValue.BooleanValue;
import dev.flagwire.domain.evaluation.AttributeValue.NumberValue;
import dev.flagwire.domain.evaluation.AttributeValue.TextValue;
import dev.flagwire.domain.evaluation.Condition;
import dev.flagwire.domain.evaluation.JsonValue;
import dev.flagwire.domain.evaluation.Operator;
import dev.flagwire.domain.json.Json;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConditionJsonTest {

  @Test
  void roundTripsAnAttributeCondition() {
    Condition condition =
        new Condition.Attribute(
            "plan",
            Operator.IN,
            List.of(new TextValue("pro"), new NumberValue(2), new BooleanValue(true)),
            true);

    assertThat(ConditionJson.fromJson("c", ConditionJson.toJson(condition))).isEqualTo(condition);
  }

  @Test
  void roundTripsASegmentCondition() {
    Condition condition = new Condition.SegmentMembership("beta", false);

    assertThat(ConditionJson.fromJson("c", ConditionJson.toJson(condition))).isEqualTo(condition);
  }

  @Test
  void defaultsNegateToFalse() {
    JsonValue json =
        Json.object()
            .text("attribute", "a")
            .text("operator", "equals")
            .put("values", new JsonValue.JsonArray(List.of(Json.text("x"))))
            .build();

    assertThat(ConditionJson.attributeFromJson("c", json).negate()).isFalse();
  }

  @Test
  void rejectsUnknownOperatorsAndBadValues() {
    JsonValue unknown =
        Json.object()
            .text("attribute", "a")
            .text("operator", "matches")
            .put("values", new JsonValue.JsonArray(List.of(Json.text("x"))))
            .build();
    JsonValue nested =
        Json.object()
            .text("attribute", "a")
            .text("operator", "equals")
            .put("values", new JsonValue.JsonArray(List.of(new JsonValue.JsonObject(Map.of()))))
            .build();

    assertThatThrownBy(() -> ConditionJson.fromJson("c", unknown))
        .isInstanceOf(FlagwireException.class)
        .hasMessageContaining("unknown operator matches");
    assertThatThrownBy(() -> ConditionJson.fromJson("c", nested))
        .isInstanceOf(FlagwireException.class)
        .hasMessageContaining("strings, numbers or booleans");
  }
}
