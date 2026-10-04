package dev.flagtide.domain.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonTextTest {

  private static final ObjectMapper JACKSON = new ObjectMapper();

  @Test
  void parsesEveryKindOfValue() {
    JsonValue parsed =
        JsonText.parse(" {\"a\": [1, -2.5, 1e3, true, false, null], \"b\": \"x\", \"c\": {}} ");

    assertThat(parsed)
        .isEqualTo(
            new JsonValue.JsonObject(
                Map.of(
                    "a",
                    new JsonValue.JsonArray(
                        List.of(
                            new JsonValue.JsonNumber(1),
                            new JsonValue.JsonNumber(-2.5),
                            new JsonValue.JsonNumber(1000),
                            new JsonValue.JsonBoolean(true),
                            new JsonValue.JsonBoolean(false),
                            new JsonValue.JsonNull())),
                    "b",
                    new JsonValue.JsonString("x"),
                    "c",
                    new JsonValue.JsonObject(Map.of()))));
  }

  @Test
  void parsesEscapesAndSurrogatePairs() {
    JsonValue parsed = JsonText.parse("\"a\\n\\t\\\"\\\\\\/\\b\\f\\r\\u00e9\\ud83d\\ude00\"");

    assertThat(parsed).isEqualTo(new JsonValue.JsonString("a\n\t\"\\/\b\f\r\u00e9\ud83d\ude00"));
  }

  @Test
  void writesObjectKeysInSortedOrderWithoutWhitespace() {
    Map<String, JsonValue> members = new LinkedHashMap<>();
    members.put("b", new JsonValue.JsonNumber(2));
    members.put("a", new JsonValue.JsonArray(List.of(new JsonValue.JsonNull())));

    assertThat(JsonText.write(new JsonValue.JsonObject(members)))
        .isEqualTo("{\"a\":[null],\"b\":2}");
  }

  @Test
  void writesIntegralNumbersWithoutAFractionAndKeepsOtherDoubles() {
    assertThat(JsonText.write(new JsonValue.JsonNumber(25000))).isEqualTo("25000");
    assertThat(JsonText.write(new JsonValue.JsonNumber(-0.0))).isEqualTo("0");
    assertThat(JsonText.write(new JsonValue.JsonNumber(0.1))).isEqualTo("0.1");
    assertThat(JsonText.write(new JsonValue.JsonNumber(1e20))).isEqualTo("1.0E20");
    assertThat(JsonText.parse(JsonText.write(new JsonValue.JsonNumber(1e20))))
        .isEqualTo(new JsonValue.JsonNumber(1e20));
  }

  @Test
  void refusesToWriteNumbersThatJsonCannotHold() {
    assertThatThrownBy(() -> JsonText.write(new JsonValue.JsonNumber(Double.NaN)))
        .isInstanceOf(FlagtideException.class);
    assertThatThrownBy(() -> JsonText.write(new JsonValue.JsonNumber(Double.POSITIVE_INFINITY)))
        .isInstanceOf(FlagtideException.class);
  }

  @Test
  void escapesControlCharactersAndLoneSurrogates() {
    String text = "line\nbreak\u0001\ud800x\udc00y\ud83d\ude00";

    String written = JsonText.write(new JsonValue.JsonString(text));

    assertThat(written).contains("\\n").contains("\\u0001").contains("\\ud800").contains("\\udc00");
    assertThat(JsonText.parse(written)).isEqualTo(new JsonValue.JsonString(text));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        " ",
        "{",
        "[1,",
        "[1 2]",
        "{\"a\"}",
        "{\"a\":1,}",
        "{a:1}",
        "{1:1}",
        "[1,]",
        "tru",
        "nul",
        "01",
        "-",
        "1.",
        "1e",
        "1e+",
        ".5",
        "+1",
        "\"abc",
        "\"a\nb\"",
        "\"\\x\"",
        "\"\\u12\"",
        "\"\\u12g4\"",
        "\"\\",
        "1e999",
        "{} {}",
        "[] x"
      })
  void rejectsMalformedText(String malformed) {
    assertThatThrownBy(() -> JsonText.parse(malformed)).isInstanceOf(FlagtideException.class);
  }

  @Test
  void rejectsNestingBeyondTheLimit() {
    String deep = "[".repeat(100) + "]".repeat(100);

    assertThatThrownBy(() -> JsonText.parse(deep))
        .isInstanceOf(FlagtideException.class)
        .hasMessageContaining("nesting");
  }

  @Test
  void acceptsNestingAtTheLimit() {
    String deep = "[".repeat(64) + "]".repeat(64);

    assertThat(JsonText.parse(deep)).isInstanceOf(JsonValue.JsonArray.class);
  }

  @Test
  void theLastDuplicateMemberWins() {
    assertThat(JsonText.parse("{\"a\":1,\"a\":2}"))
        .isEqualTo(new JsonValue.JsonObject(Map.of("a", new JsonValue.JsonNumber(2))));
  }

  @Property
  void writingThenParsingGivesTheSameValue(@ForAll("values") JsonValue value) {
    assertThat(JsonText.parse(JsonText.write(value))).isEqualTo(value);
  }

  @Property
  void jacksonReadsWhatWeWriteAndWeReadWhatJacksonWrites(@ForAll("values") JsonValue value)
      throws Exception {
    String written = JsonText.write(value);
    JsonNode viaJackson = JACKSON.readTree(written);

    assertThat(toValue(viaJackson)).isEqualTo(value);
    assertThat(JsonText.parse(JACKSON.writeValueAsString(viaJackson))).isEqualTo(value);
  }

  @Provide
  Arbitrary<JsonValue> values() {
    return valuesUpToDepth(3);
  }

  private static Arbitrary<JsonValue> valuesUpToDepth(int depth) {
    if (depth == 0) {
      return scalars();
    }
    Arbitrary<JsonValue> inner = valuesUpToDepth(depth - 1);
    Arbitrary<JsonValue> arrays = inner.list().ofMaxSize(4).map(JsonValue.JsonArray::new);
    Arbitrary<JsonValue> objects =
        Arbitraries.maps(Arbitraries.strings().ofMaxLength(6), inner)
            .ofMaxSize(4)
            .map(JsonValue.JsonObject::new);
    return Arbitraries.oneOf(scalars(), arrays, objects);
  }

  private static Arbitrary<JsonValue> scalars() {
    return Arbitraries.oneOf(
        Arbitraries.strings().ofMaxLength(12).map(JsonValue.JsonString::new),
        Arbitraries.doubles().between(-1e9, 1e9).map(JsonValue.JsonNumber::new),
        Arbitraries.integers().map(value -> new JsonValue.JsonNumber(value)),
        Arbitraries.of(true, false).map(JsonValue.JsonBoolean::new),
        Arbitraries.just(new JsonValue.JsonNull()));
  }

  private static JsonValue toValue(JsonNode node) {
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
      node.forEach(item -> items.add(toValue(item)));
      return new JsonValue.JsonArray(items);
    }
    Map<String, JsonValue> members = new LinkedHashMap<>();
    node.properties().forEach(member -> members.put(member.getKey(), toValue(member.getValue())));
    return new JsonValue.JsonObject(members);
  }
}
