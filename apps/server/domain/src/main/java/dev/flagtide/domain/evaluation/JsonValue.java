package dev.flagtide.domain.evaluation;

import java.util.List;
import java.util.Map;

public sealed interface JsonValue {

  record JsonNull() implements JsonValue {}

  record JsonBoolean(boolean value) implements JsonValue {}

  record JsonNumber(double value) implements JsonValue {}

  record JsonString(String value) implements JsonValue {}

  record JsonArray(List<JsonValue> items) implements JsonValue {
    public JsonArray {
      items = List.copyOf(items);
    }
  }

  record JsonObject(Map<String, JsonValue> members) implements JsonValue {
    public JsonObject {
      members = Map.copyOf(members);
    }
  }
}
