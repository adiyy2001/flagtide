package dev.flagwire.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AttributeValueTest {

  @Test
  void convertsScalarsAndLists() {
    assertThat(AttributeValue.fromJson(new JsonValue.JsonString("x")))
        .contains(new AttributeValue.TextValue("x"));
    assertThat(AttributeValue.fromJson(new JsonValue.JsonNumber(1.5)))
        .contains(new AttributeValue.NumberValue(1.5));
    assertThat(AttributeValue.fromJson(new JsonValue.JsonBoolean(true)))
        .contains(new AttributeValue.BooleanValue(true));
    assertThat(
            AttributeValue.fromJson(
                new JsonValue.JsonArray(
                    List.of(new JsonValue.JsonString("a"), new JsonValue.JsonNumber(2)))))
        .contains(
            new AttributeValue.ListValue(
                List.of(new AttributeValue.TextValue("a"), new AttributeValue.NumberValue(2))));
  }

  @Test
  void treatsNullObjectsAndNestedStructuresAsMissing() {
    assertThat(AttributeValue.fromJson(new JsonValue.JsonNull())).isEmpty();
    assertThat(AttributeValue.fromJson(new JsonValue.JsonObject(Map.of()))).isEmpty();
    assertThat(
            AttributeValue.fromJson(
                new JsonValue.JsonArray(
                    List.of(new JsonValue.JsonArray(List.of(new JsonValue.JsonNumber(1)))))))
        .isEmpty();
    assertThat(
            AttributeValue.fromJson(
                new JsonValue.JsonArray(
                    List.of(new JsonValue.JsonString("a"), new JsonValue.JsonNull()))))
        .isEmpty();
  }

  @Test
  void treatsNonFiniteNumbersAsMissing() {
    assertThat(AttributeValue.fromJson(new JsonValue.JsonNumber(Double.POSITIVE_INFINITY)))
        .isEmpty();
    assertThat(AttributeValue.fromJson(new JsonValue.JsonNumber(Double.NaN))).isEmpty();
  }

  @Test
  void contextDropsMissingAttributes() {
    EvaluationContext context =
        EvaluationContext.of(
            "u",
            Map.of("kept", new JsonValue.JsonString("x"), "dropped", new JsonValue.JsonNull()));

    assertThat(context.attribute("kept")).isPresent();
    assertThat(context.attribute("dropped")).isEmpty();
    assertThat(context.attribute("absent")).isEmpty();
  }
}
