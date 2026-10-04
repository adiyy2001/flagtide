package dev.flagtide.bootstrap;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(MemoryProfile.class)
class MemoryOpenApiDriftTest {

  private static final String UPDATE_PROPERTY = "flagtide.openapi.update";
  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  private static Path committedDocument() {
    return Path.of(System.getProperty("flagtide.server.root")).resolve("openapi.json");
  }

  private static String pretty(JsonNode document) throws IOException {
    Object sorted = MAPPER.treeToValue(document, Object.class);
    return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(sorted) + "\n";
  }

  @Test
  void theCommittedDocumentMatchesTheGeneratedOne() throws IOException {
    String generated =
        given().get("/q/openapi?format=json").then().statusCode(200).extract().asString();
    JsonNode generatedTree = MAPPER.readTree(generated);
    Path committed = committedDocument();
    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      Files.writeString(committed, pretty(generatedTree), StandardCharsets.UTF_8);
    }

    assertThat(committed)
        .as("run the build with -D%s=true to regenerate %s", UPDATE_PROPERTY, committed)
        .exists();
    JsonNode committedTree = MAPPER.readTree(Files.readString(committed, StandardCharsets.UTF_8));
    assertThat(generatedTree)
        .as("apps/server/openapi.json drifted, rerun with -D%s=true and commit", UPDATE_PROPERTY)
        .isEqualTo(committedTree);
  }

  @Test
  void everyOperationHasASummaryAndATag() throws IOException {
    JsonNode paths = MAPPER.readTree(given().get("/q/openapi?format=json").asString()).get("paths");

    paths.forEach(
        path ->
            path.forEach(
                operation -> {
                  assertThat(operation.has("summary")).isTrue();
                  assertThat(operation.get("tags")).isNotEmpty();
                }));
  }
}
