package dev.flagtide.domain.condition;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.AttributeValue;
import dev.flagtide.domain.evaluation.Condition;
import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.evaluation.Operator;
import dev.flagtide.domain.json.Json;
import dev.flagtide.domain.json.JsonFields;
import java.util.List;

public final class ConditionJson {

  private ConditionJson() {}

  public static JsonValue toJson(Condition condition) {
    return switch (condition) {
      case Condition.Attribute attribute ->
          Json.object()
              .text("attribute", attribute.attribute())
              .text("operator", attribute.operator().wireName())
              .put("values", Json.array(attribute.values(), ConditionJson::scalarToJson))
              .bool("negate", attribute.negate())
              .build();
      case Condition.SegmentMembership membership ->
          Json.object()
              .text("segment", membership.segment())
              .bool("negate", membership.negate())
              .build();
    };
  }

  public static Condition fromJson(String context, JsonValue json) {
    JsonFields fields = JsonFields.of(context, json);
    boolean negate = fields.boolOr("negate", false);
    if (fields.find("segment").isPresent()) {
      return new Condition.SegmentMembership(fields.text("segment"), negate);
    }
    return attributeFromFields(fields, negate);
  }

  public static Condition.Attribute attributeFromJson(String context, JsonValue json) {
    JsonFields fields = JsonFields.of(context, json);
    return attributeFromFields(fields, fields.boolOr("negate", false));
  }

  private static Condition.Attribute attributeFromFields(JsonFields fields, boolean negate) {
    String wireName = fields.text("operator");
    Operator operator =
        Operator.fromWireName(wireName)
            .orElseThrow(
                () ->
                    FlagtideException.invalid(
                        fields.context() + ".operator", "unknown operator " + wireName));
    List<AttributeValue.Scalar> values =
        fields.array("values").stream()
            .map(value -> scalarFromJson(fields.context() + ".values", value))
            .toList();
    return new Condition.Attribute(fields.text("attribute"), operator, values, negate);
  }

  private static JsonValue scalarToJson(AttributeValue.Scalar scalar) {
    return switch (scalar) {
      case AttributeValue.TextValue text -> Json.text(text.value());
      case AttributeValue.NumberValue number -> Json.number(number.value());
      case AttributeValue.BooleanValue flag -> Json.bool(flag.value());
    };
  }

  private static AttributeValue.Scalar scalarFromJson(String context, JsonValue json) {
    return switch (json) {
      case JsonValue.JsonString text -> new AttributeValue.TextValue(text.value());
      case JsonValue.JsonNumber number -> new AttributeValue.NumberValue(number.value());
      case JsonValue.JsonBoolean flag -> new AttributeValue.BooleanValue(flag.value());
      default -> throw FlagtideException.invalid(context, "must hold strings, numbers or booleans");
    };
  }
}
