package dev.flagwire.domain.evaluation;

import java.util.List;
import java.util.Optional;

public sealed interface AttributeValue {

  sealed interface Scalar extends AttributeValue {
    @Override
    default List<Scalar> elements() {
      return List.of(this);
    }
  }

  record TextValue(String value) implements Scalar {}

  record NumberValue(double value) implements Scalar {}

  record BooleanValue(boolean value) implements Scalar {}

  record ListValue(List<Scalar> elements) implements AttributeValue {
    public ListValue {
      elements = List.copyOf(elements);
    }
  }

  List<Scalar> elements();

  static Optional<AttributeValue> fromJson(JsonValue json) {
    return switch (json) {
      case JsonValue.JsonString text -> Optional.of(new TextValue(text.value()));
      case JsonValue.JsonNumber number ->
          Double.isFinite(number.value())
              ? Optional.of(new NumberValue(number.value()))
              : Optional.empty();
      case JsonValue.JsonBoolean flag -> Optional.of(new BooleanValue(flag.value()));
      case JsonValue.JsonArray array -> listFromJson(array);
      case JsonValue.JsonNull ignored -> Optional.empty();
      case JsonValue.JsonObject ignored -> Optional.empty();
    };
  }

  private static Optional<AttributeValue> listFromJson(JsonValue.JsonArray array) {
    List<Optional<AttributeValue>> converted =
        array.items().stream().map(AttributeValue::fromJson).toList();
    boolean allScalars =
        converted.stream().allMatch(item -> item.isPresent() && item.get() instanceof Scalar);
    if (!allScalars) {
      return Optional.empty();
    }
    return Optional.of(
        new ListValue(converted.stream().map(item -> (Scalar) item.orElseThrow()).toList()));
  }
}
