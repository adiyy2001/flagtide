package dev.flagwire.domain.evaluation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record EvaluationContext(String key, Map<String, AttributeValue> attributes) {
  public EvaluationContext {
    attributes = Map.copyOf(attributes);
  }

  public static EvaluationContext of(String key, Map<String, JsonValue> attributes) {
    Map<String, AttributeValue> usable = new HashMap<>();
    attributes.forEach(
        (name, json) -> AttributeValue.fromJson(json).ifPresent(value -> usable.put(name, value)));
    return new EvaluationContext(key, usable);
  }

  public Optional<AttributeValue> attribute(String name) {
    return Optional.ofNullable(this.attributes.get(name));
  }
}
