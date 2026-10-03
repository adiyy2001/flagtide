package dev.flagwire.domain.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.evaluation.JsonValue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonFieldsTest {

  private static JsonFields sample() {
    return JsonFields.of(
        "doc",
        Json.object()
            .text("name", "x")
            .bool("on", true)
            .number("count", 3)
            .number("ratio", 1.5)
            .put("nothing", new JsonValue.JsonNull())
            .put("items", new JsonValue.JsonArray(List.of(Json.text("a"))))
            .put("child", Json.object().text("k", "v").build())
            .build());
  }

  @Test
  void readsTypedFields() {
    JsonFields fields = sample();

    assertThat(fields.text("name")).isEqualTo("x");
    assertThat(fields.optionalText("name")).contains("x");
    assertThat(fields.optionalText("absent")).isEmpty();
    assertThat(fields.bool("on")).isTrue();
    assertThat(fields.boolOr("absent", true)).isTrue();
    assertThat(fields.boolOr("on", false)).isTrue();
    assertThat(fields.integer("count")).isEqualTo(3);
    assertThat(fields.array("items")).hasSize(1);
    assertThat(fields.object("child").text("k")).isEqualTo("v");
    assertThat(fields.find("nothing")).isEmpty();
    assertThat(fields.members()).containsKey("name");
    assertThat(fields.context()).isEqualTo("doc");
  }

  @Test
  void reportsWhichFieldIsWrong() {
    JsonFields fields = sample();

    assertThatThrownBy(() -> fields.text("absent")).hasMessageContaining("doc.absent: is required");
    assertThatThrownBy(() -> fields.text("count")).hasMessageContaining("must be a string");
    assertThatThrownBy(() -> fields.bool("name")).hasMessageContaining("must be a boolean");
    assertThatThrownBy(() -> fields.integer("ratio")).hasMessageContaining("must be an integer");
    assertThatThrownBy(() -> fields.integer("name")).hasMessageContaining("must be an integer");
    assertThatThrownBy(() -> fields.array("name")).hasMessageContaining("must be an array");
    assertThatThrownBy(() -> fields.object("name")).isInstanceOf(FlagwireException.class);
    assertThatThrownBy(() -> JsonFields.of("doc", Json.text("x")))
        .hasMessageContaining("must be a JSON object");
  }

  @Test
  void checksStandaloneValues() {
    assertThat(Json.requireText("c", Json.text("x"))).isEqualTo("x");
    assertThat(Json.requireArray("c", new JsonValue.JsonArray(List.of()))).isEmpty();
    assertThatThrownBy(() -> Json.requireText("c", Json.number(1)))
        .isInstanceOf(FlagwireException.class);
    assertThatThrownBy(() -> Json.requireArray("c", Json.text("x")))
        .isInstanceOf(FlagwireException.class);
    assertThat(new JsonValue.JsonObject(Map.of()).members()).isEmpty();
  }
}
