package dev.flagtide.domain.segment;

import dev.flagtide.domain.condition.ConditionJson;
import dev.flagtide.domain.evaluation.Condition;
import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.json.Json;
import dev.flagtide.domain.json.JsonFields;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.Revision;
import dev.flagtide.domain.value.SegmentKey;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class SegmentDocument {

  private SegmentDocument() {}

  public static JsonValue toJson(Segment segment) {
    return Json.object()
        .text("environment", segment.environment().value())
        .text("key", segment.key().value())
        .text("name", segment.name())
        .number("revision", segment.revision().value())
        .text("createdAt", segment.createdAt().toString())
        .text("updatedAt", segment.updatedAt().toString())
        .put("included", sortedTexts(segment.included()))
        .put("excluded", sortedTexts(segment.excluded()))
        .put(
            "rules", Json.array(segment.rules(), group -> Json.array(group, ConditionJson::toJson)))
        .build();
  }

  public static Segment fromJson(JsonValue json) {
    JsonFields fields = JsonFields.of("segment", json);
    return new Segment(
        new EnvironmentKey(fields.text("environment")),
        new SegmentKey(fields.text("key")),
        fields.text("name"),
        texts(fields.array("included")),
        texts(fields.array("excluded")),
        fields.array("rules").stream().map(SegmentDocument::groupFromJson).toList(),
        new Revision(fields.integer("revision")),
        ZonedDateTime.parse(fields.text("createdAt")),
        ZonedDateTime.parse(fields.text("updatedAt")));
  }

  private static JsonValue sortedTexts(Collection<String> values) {
    return new JsonValue.JsonArray(values.stream().sorted().map(Json::text).toList());
  }

  private static Set<String> texts(List<JsonValue> values) {
    return values.stream()
        .map(value -> Json.requireText("segment.members", value))
        .collect(Collectors.toSet());
  }

  private static List<Condition.Attribute> groupFromJson(JsonValue json) {
    return Json.requireArray("segment.rules", json).stream()
        .map(item -> ConditionJson.attributeFromJson("segment.rules", item))
        .toList();
  }
}
