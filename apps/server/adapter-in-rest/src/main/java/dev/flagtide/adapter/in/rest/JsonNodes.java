package dev.flagtide.adapter.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.JsonValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class JsonNodes {

  private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;
  private static final double LARGEST_EXACT_INTEGER = 1e15;

  private JsonNodes() {}

  public static JsonValue toValue(String field, JsonNode node) {
    Required.field(field, node);
    if (node.isNull()) {
      return new JsonValue.JsonNull();
    }
    if (node.isBoolean()) {
      return new JsonValue.JsonBoolean(node.booleanValue());
    }
    if (node.isNumber()) {
      return new JsonValue.JsonNumber(node.doubleValue());
    }
    if (node.isTextual()) {
      return new JsonValue.JsonString(node.textValue());
    }
    if (node.isArray()) {
      List<JsonValue> items = new ArrayList<>();
      node.forEach(item -> items.add(toValue(field, item)));
      return new JsonValue.JsonArray(items);
    }
    if (node.isObject()) {
      Map<String, JsonValue> members = new TreeMap<>();
      node.properties()
          .forEach(entry -> members.put(entry.getKey(), toValue(field, entry.getValue())));
      return new JsonValue.JsonObject(members);
    }
    throw FlagtideException.invalid(field, "is not a JSON value");
  }

  public static JsonNode toNode(JsonValue value) {
    return switch (value) {
      case JsonValue.JsonNull ignored -> FACTORY.nullNode();
      case JsonValue.JsonBoolean flag -> FACTORY.booleanNode(flag.value());
      case JsonValue.JsonNumber number -> number(number.value());
      case JsonValue.JsonString text -> FACTORY.textNode(text.value());
      case JsonValue.JsonArray array -> {
        ArrayNode node = FACTORY.arrayNode();
        array.items().forEach(item -> node.add(toNode(item)));
        yield node;
      }
      case JsonValue.JsonObject object -> {
        ObjectNode node = FACTORY.objectNode();
        new TreeMap<>(object.members()).forEach((name, member) -> node.set(name, toNode(member)));
        yield node;
      }
    };
  }

  private static JsonNode number(double value) {
    boolean integral = value == Math.rint(value) && Math.abs(value) < LARGEST_EXACT_INTEGER;
    return integral ? FACTORY.numberNode((long) value) : FACTORY.numberNode(value);
  }
}
