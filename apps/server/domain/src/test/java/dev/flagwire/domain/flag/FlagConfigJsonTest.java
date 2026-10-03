package dev.flagwire.domain.flag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.evaluation.FlagConfig;
import dev.flagwire.domain.evaluation.JsonValue;
import dev.flagwire.domain.evaluation.Segment;
import dev.flagwire.domain.json.JsonText;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FlagConfigJsonTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static List<JsonNode> evaluationCases() throws IOException {
    Path directory = Path.of(System.getProperty("flagwire.vectors"));
    List<JsonNode> cases = new ArrayList<>();
    try (Stream<Path> files = Files.list(directory)) {
      for (Path file :
          files.filter(path -> path.getFileName().toString().startsWith("eval-")).toList()) {
        MAPPER.readTree(Files.readString(file)).get("cases").forEach(cases::add);
      }
    }
    return cases;
  }

  @Test
  void roundTripsEveryFlagAndSegmentOfTheSharedVectors() throws IOException {
    List<JsonNode> cases = evaluationCases();
    int flags = 0;
    int segments = 0;
    for (JsonNode evaluationCase : cases) {
      String flagText = evaluationCase.get("flag").toString();
      FlagConfig config = FlagConfigJson.fromJson(JsonText.parse(flagText));
      assertThat(
              FlagConfigJson.fromJson(
                  JsonText.parse(JsonText.write(FlagConfigJson.toJson(config)))))
          .isEqualTo(config);
      flags++;
      for (JsonNode segmentNode : evaluationCase.get("segments")) {
        Segment segment = FlagConfigJson.segmentFromJson(JsonText.parse(segmentNode.toString()));
        assertThat(
                FlagConfigJson.segmentFromJson(
                    JsonText.parse(JsonText.write(FlagConfigJson.segmentToJson(segment)))))
            .isEqualTo(segment);
        segments++;
      }
    }
    assertThat(flags).isGreaterThanOrEqualTo(200);
    assertThat(segments).isPositive();
  }

  @Test
  void writesTheWireFormatOfTheSpec() {
    FlagConfig config =
        FlagConfigJson.fromJson(
            JsonText.parse(
                """
                {"key":"f","type":"string","enabled":true,"killSwitch":false,"salt":"ab",
                 "variants":[{"key":"a","value":"x"},{"key":"b","value":"y"}],
                 "offVariant":"b",
                 "rules":[{"id":"r1","conditions":[{"attribute":"plan","operator":"in","values":["pro"],"negate":false},
                   {"segment":"beta","negate":true}],
                   "serve":{"rollout":[{"variant":"a","weight":25000},{"variant":"b","weight":75000}]}}],
                 "fallthrough":{"variant":"b"}}
                """));

    String written = JsonText.write(FlagConfigJson.toJson(config));

    assertThat(written)
        .contains("\"type\":\"string\"")
        .contains("\"weight\":25000")
        .contains("\"segment\":\"beta\"")
        .contains("\"fallthrough\":{\"variant\":\"b\"}");
  }

  @Test
  void aVariantWithoutAValueReadsAsNull() {
    FlagConfig config =
        FlagConfigJson.fromJson(
            JsonText.parse(
                """
                {"key":"f","type":"json","enabled":true,"killSwitch":false,"salt":"ab",
                 "variants":[{"key":"a"}],"offVariant":"a","rules":[],"fallthrough":{"variant":"a"}}
                """));

    assertThat(config.variants().get(0).value()).isEqualTo(new JsonValue.JsonNull());
  }

  @Test
  void rejectsAnUnknownFlagType() {
    assertThatThrownBy(
            () ->
                FlagConfigJson.fromJson(
                    JsonText.parse(
                        """
                        {"key":"f","type":"uuid","enabled":true,"killSwitch":false,"salt":"ab",
                         "variants":[],"offVariant":"a","rules":[],"fallthrough":{"variant":"a"}}
                        """)))
        .isInstanceOf(FlagwireException.class)
        .hasMessageContaining("unknown flag type");
  }

  @Test
  void rejectsASegmentWithNonTextMembers() {
    assertThatThrownBy(
            () ->
                FlagConfigJson.segmentFromJson(
                    JsonText.parse(
                        "{\"key\":\"s\",\"included\":[1],\"excluded\":[],\"rules\":[]}")))
        .isInstanceOf(FlagwireException.class);
  }
}
