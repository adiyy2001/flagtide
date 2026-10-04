package dev.flagtide.domain.json;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.JsonValue;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class Json {

  private Json() {}

  public static JsonValue text(String value) {
    return new JsonValue.JsonString(value);
  }

  public static JsonValue number(double value) {
    return new JsonValue.JsonNumber(value);
  }

  public static JsonValue bool(boolean value) {
    return new JsonValue.JsonBoolean(value);
  }

  public static <T> JsonValue array(Collection<T> items, Function<T, JsonValue> mapper) {
    return new JsonValue.JsonArray(items.stream().map(mapper).toList());
  }

  public static String requireText(String context, JsonValue value) {
    if (value instanceof JsonValue.JsonString text) {
      return text.value();
    }
    throw FlagtideException.invalid(context, "must be a string");
  }

  public static List<JsonValue> requireArray(String context, JsonValue value) {
    if (value instanceof JsonValue.JsonArray array) {
      return array.items();
    }
    throw FlagtideException.invalid(context, "must be an array");
  }

  public static ObjectBuilder object() {
    return new ObjectBuilder();
  }

  public static final class ObjectBuilder {

    private final Map<String, JsonValue> members = new HashMap<>();

    public ObjectBuilder put(String name, JsonValue value) {
      this.members.put(name, value);
      return this;
    }

    public ObjectBuilder text(String name, String value) {
      return this.put(name, Json.text(value));
    }

    public ObjectBuilder number(String name, double value) {
      return this.put(name, Json.number(value));
    }

    public ObjectBuilder bool(String name, boolean value) {
      return this.put(name, Json.bool(value));
    }

    public JsonValue build() {
      return new JsonValue.JsonObject(this.members);
    }
  }
}
