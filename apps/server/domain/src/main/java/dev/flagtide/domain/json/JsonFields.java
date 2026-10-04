package dev.flagtide.domain.json;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.JsonValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JsonFields {

  private final String context;
  private final Map<String, JsonValue> members;

  private JsonFields(String context, Map<String, JsonValue> members) {
    this.context = context;
    this.members = members;
  }

  public static JsonFields of(String context, JsonValue value) {
    if (value instanceof JsonValue.JsonObject object) {
      return new JsonFields(context, object.members());
    }
    throw FlagtideException.invalid(context, "must be a JSON object");
  }

  public Optional<JsonValue> find(String name) {
    return Optional.ofNullable(this.members.get(name))
        .filter(value -> !(value instanceof JsonValue.JsonNull));
  }

  public JsonValue require(String name) {
    return this.find(name).orElseThrow(() -> this.missing(name));
  }

  public String text(String name) {
    if (this.require(name) instanceof JsonValue.JsonString text) {
      return text.value();
    }
    throw this.wrongType(name, "a string");
  }

  public Optional<String> optionalText(String name) {
    return this.find(name).map(value -> this.text(name));
  }

  public boolean bool(String name) {
    if (this.require(name) instanceof JsonValue.JsonBoolean flag) {
      return flag.value();
    }
    throw this.wrongType(name, "a boolean");
  }

  public boolean boolOr(String name, boolean fallback) {
    return this.find(name).map(value -> this.bool(name)).orElse(fallback);
  }

  public long integer(String name) {
    if (this.require(name) instanceof JsonValue.JsonNumber number
        && number.value() == Math.rint(number.value())) {
      return (long) number.value();
    }
    throw this.wrongType(name, "an integer");
  }

  public List<JsonValue> array(String name) {
    if (this.require(name) instanceof JsonValue.JsonArray array) {
      return array.items();
    }
    throw this.wrongType(name, "an array");
  }

  public JsonFields object(String name) {
    return JsonFields.of(this.context + "." + name, this.require(name));
  }

  public Map<String, JsonValue> members() {
    return this.members;
  }

  public String context() {
    return this.context;
  }

  private FlagtideException missing(String name) {
    return FlagtideException.invalid(this.context + "." + name, "is required");
  }

  private FlagtideException wrongType(String name, String expected) {
    return FlagtideException.invalid(this.context + "." + name, "must be " + expected);
  }
}
