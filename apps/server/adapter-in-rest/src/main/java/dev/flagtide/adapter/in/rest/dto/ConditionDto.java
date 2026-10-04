package dev.flagtide.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.flagtide.adapter.in.rest.Required;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.AttributeValue;
import dev.flagtide.domain.evaluation.Condition;
import dev.flagtide.domain.evaluation.Operator;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
    name = "Condition",
    description =
        "Either an attribute condition (attribute, operator, values) or a segment condition"
            + " (segment)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConditionDto(
    String attribute, String operator, List<JsonNode> values, Boolean negate, String segment) {

  public static ConditionDto from(Condition condition) {
    return switch (condition) {
      case Condition.Attribute attribute ->
          new ConditionDto(
              attribute.attribute(),
              attribute.operator().wireName(),
              attribute.values().stream().map(ConditionDto::scalarNode).toList(),
              attribute.negate(),
              null);
      case Condition.SegmentMembership membership ->
          new ConditionDto(null, null, null, membership.negate(), membership.segment());
    };
  }

  public Condition toDomain(String field) {
    if (this.segment != null) {
      this.requireOnlySegment(field);
      return new Condition.SegmentMembership(this.segment, this.isNegated());
    }
    return this.toAttribute(field);
  }

  public Condition.Attribute toAttribute(String field) {
    if (this.segment != null) {
      throw FlagtideException.invalid(field, "a segment condition is not allowed here");
    }
    String wireName = Required.text(field + ".operator", this.operator);
    Operator parsed =
        Operator.fromWireName(wireName)
            .orElseThrow(
                () ->
                    FlagtideException.invalid(field + ".operator", "unknown operator " + wireName));
    List<AttributeValue.Scalar> scalars = new ArrayList<>();
    Required.items(field + ".values", this.values)
        .forEach(value -> scalars.add(scalar(field + ".values", value)));
    return new Condition.Attribute(
        Required.text(field + ".attribute", this.attribute), parsed, scalars, this.isNegated());
  }

  private boolean isNegated() {
    return this.negate != null && this.negate;
  }

  private void requireOnlySegment(String field) {
    if (this.attribute != null || this.operator != null || this.values != null) {
      throw FlagtideException.invalid(
          field, "a segment condition must not carry attribute, operator or values");
    }
  }

  private static AttributeValue.Scalar scalar(String field, JsonNode node) {
    if (node.isTextual()) {
      return new AttributeValue.TextValue(node.textValue());
    }
    if (node.isNumber()) {
      return new AttributeValue.NumberValue(node.doubleValue());
    }
    if (node.isBoolean()) {
      return new AttributeValue.BooleanValue(node.booleanValue());
    }
    throw FlagtideException.invalid(field, "must hold strings, numbers or booleans");
  }

  private static JsonNode scalarNode(AttributeValue.Scalar scalar) {
    JsonNodeFactory factory = JsonNodeFactory.instance;
    return switch (scalar) {
      case AttributeValue.TextValue text -> factory.textNode(text.value());
      case AttributeValue.NumberValue number -> numberNode(number.value());
      case AttributeValue.BooleanValue flag -> factory.booleanNode(flag.value());
    };
  }

  private static JsonNode numberNode(double value) {
    JsonNodeFactory factory = JsonNodeFactory.instance;
    boolean integral = value == Math.rint(value) && Math.abs(value) < 1e15;
    return integral ? factory.numberNode((long) value) : factory.numberNode(value);
  }
}
