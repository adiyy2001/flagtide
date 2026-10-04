package dev.flagtide.domain.json;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonText {

  private static final int MAX_DEPTH = 64;

  private final String text;
  private int position;

  private JsonText(String text) {
    this.text = text;
  }

  public static JsonValue parse(String text) {
    JsonText parser = new JsonText(text);
    parser.skipWhitespace();
    JsonValue value = parser.readValue(0);
    parser.skipWhitespace();
    if (parser.position != text.length()) {
      throw parser.failure("unexpected content after the value");
    }
    return value;
  }

  public static String write(JsonValue value) {
    StringBuilder out = new StringBuilder();
    append(out, value);
    return out.toString();
  }

  private static void append(StringBuilder out, JsonValue value) {
    switch (value) {
      case JsonValue.JsonNull ignored -> out.append("null");
      case JsonValue.JsonBoolean flag -> out.append(flag.value());
      case JsonValue.JsonNumber number -> out.append(formatNumber(number.value()));
      case JsonValue.JsonString string -> appendString(out, string.value());
      case JsonValue.JsonArray array -> appendArray(out, array);
      case JsonValue.JsonObject object -> appendObject(out, object);
    }
  }

  private static void appendArray(StringBuilder out, JsonValue.JsonArray array) {
    out.append('[');
    for (int index = 0; index < array.items().size(); index++) {
      if (index > 0) {
        out.append(',');
      }
      append(out, array.items().get(index));
    }
    out.append(']');
  }

  private static void appendObject(StringBuilder out, JsonValue.JsonObject object) {
    out.append('{');
    boolean first = true;
    for (String name : object.members().keySet().stream().sorted().toList()) {
      if (!first) {
        out.append(',');
      }
      first = false;
      appendString(out, name);
      out.append(':');
      append(out, object.members().get(name));
    }
    out.append('}');
  }

  private static String formatNumber(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      throw FlagtideException.invalid("json", "NaN and infinity cannot be written as JSON");
    }
    boolean integral = value == Math.rint(value) && Math.abs(value) < 1e15;
    if (integral) {
      return Long.toString((long) value);
    }
    return Double.toString(value);
  }

  private static void appendString(StringBuilder out, String value) {
    out.append('"');
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      switch (current) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        default -> {
          if (needsUnicodeEscape(value, index)) {
            out.append(String.format("\\u%04x", (int) current));
          } else {
            out.append(current);
          }
        }
      }
    }
    out.append('"');
  }

  private static boolean needsUnicodeEscape(String value, int index) {
    char current = value.charAt(index);
    if (current < 0x20) {
      return true;
    }
    if (Character.isHighSurrogate(current)) {
      return index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1));
    }
    if (Character.isLowSurrogate(current)) {
      return index == 0 || !Character.isHighSurrogate(value.charAt(index - 1));
    }
    return false;
  }

  private JsonValue readValue(int depth) {
    if (depth > MAX_DEPTH) {
      throw this.failure("nesting is deeper than " + MAX_DEPTH);
    }
    if (this.position >= this.text.length()) {
      throw this.failure("unexpected end of input");
    }
    char current = this.text.charAt(this.position);
    return switch (current) {
      case '{' -> this.readObject(depth);
      case '[' -> this.readArray(depth);
      case '"' -> new JsonValue.JsonString(this.readString());
      case 't' -> this.readLiteral("true", new JsonValue.JsonBoolean(true));
      case 'f' -> this.readLiteral("false", new JsonValue.JsonBoolean(false));
      case 'n' -> this.readLiteral("null", new JsonValue.JsonNull());
      default -> this.readNumber();
    };
  }

  private JsonValue readLiteral(String literal, JsonValue value) {
    if (!this.text.startsWith(literal, this.position)) {
      throw this.failure("unexpected token");
    }
    this.position += literal.length();
    return value;
  }

  private JsonValue readObject(int depth) {
    this.position++;
    Map<String, JsonValue> members = new LinkedHashMap<>();
    this.skipWhitespace();
    if (this.consumeIf('}')) {
      return new JsonValue.JsonObject(members);
    }
    do {
      String name = this.readName();
      this.skipWhitespace();
      this.expect(':');
      this.skipWhitespace();
      members.put(name, this.readValue(depth + 1));
      this.skipWhitespace();
    } while (this.consumeIf(','));
    this.expect('}');
    return new JsonValue.JsonObject(members);
  }

  private String readName() {
    this.skipWhitespace();
    if (this.position >= this.text.length() || this.text.charAt(this.position) != '"') {
      throw this.failure("expected a member name");
    }
    return this.readString();
  }

  private JsonValue readArray(int depth) {
    this.position++;
    List<JsonValue> items = new ArrayList<>();
    this.skipWhitespace();
    if (this.consumeIf(']')) {
      return new JsonValue.JsonArray(items);
    }
    do {
      this.skipWhitespace();
      items.add(this.readValue(depth + 1));
      this.skipWhitespace();
    } while (this.consumeIf(','));
    this.expect(']');
    return new JsonValue.JsonArray(items);
  }

  private String readString() {
    this.position++;
    StringBuilder out = new StringBuilder();
    while (true) {
      if (this.position >= this.text.length()) {
        throw this.failure("unterminated string");
      }
      char current = this.text.charAt(this.position++);
      if (current == '"') {
        return out.toString();
      }
      if (current < 0x20) {
        throw this.failure("control character in string");
      }
      if (current == '\\') {
        out.append(this.readEscape());
      } else {
        out.append(current);
      }
    }
  }

  private char readEscape() {
    if (this.position >= this.text.length()) {
      throw this.failure("unterminated escape");
    }
    char escaped = this.text.charAt(this.position++);
    return switch (escaped) {
      case '"' -> '"';
      case '\\' -> '\\';
      case '/' -> '/';
      case 'b' -> '\b';
      case 'f' -> '\f';
      case 'n' -> '\n';
      case 'r' -> '\r';
      case 't' -> '\t';
      case 'u' -> this.readUnicodeEscape();
      default -> throw this.failure("invalid escape");
    };
  }

  private char readUnicodeEscape() {
    if (this.position + 4 > this.text.length()) {
      throw this.failure("incomplete unicode escape");
    }
    String digits = this.text.substring(this.position, this.position + 4);
    if (!digits.chars().allMatch(character -> Character.digit(character, 16) >= 0)) {
      throw this.failure("invalid unicode escape");
    }
    this.position += 4;
    return (char) Integer.parseInt(digits, 16);
  }

  private JsonValue readNumber() {
    int start = this.position;
    this.consumeIf('-');
    this.readIntegerPart();
    if (this.consumeIf('.')) {
      this.readDigits("fraction");
    }
    if (this.consumeIf('e') || this.consumeIf('E')) {
      if (!this.consumeIf('+')) {
        this.consumeIf('-');
      }
      this.readDigits("exponent");
    }
    double value = Double.parseDouble(this.text.substring(start, this.position));
    if (Double.isInfinite(value)) {
      throw this.failure("number out of range");
    }
    return new JsonValue.JsonNumber(value);
  }

  private void readIntegerPart() {
    if (this.consumeIf('0')) {
      return;
    }
    this.readDigits("number");
  }

  private void readDigits(String what) {
    int start = this.position;
    while (this.position < this.text.length() && isDigit(this.text.charAt(this.position))) {
      this.position++;
    }
    if (this.position == start) {
      throw this.failure("expected digits in " + what);
    }
  }

  private static boolean isDigit(char character) {
    return character >= '0' && character <= '9';
  }

  private void skipWhitespace() {
    while (this.position < this.text.length()) {
      char current = this.text.charAt(this.position);
      if (current != ' ' && current != '\t' && current != '\n' && current != '\r') {
        return;
      }
      this.position++;
    }
  }

  private boolean consumeIf(char expected) {
    if (this.position < this.text.length() && this.text.charAt(this.position) == expected) {
      this.position++;
      return true;
    }
    return false;
  }

  private void expect(char expected) {
    if (!this.consumeIf(expected)) {
      throw this.failure("expected '" + expected + "'");
    }
  }

  private FlagtideException failure(String message) {
    return FlagtideException.invalid("json", message + " at position " + this.position);
  }
}
