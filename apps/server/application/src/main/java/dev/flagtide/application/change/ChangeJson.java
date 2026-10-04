package dev.flagtide.application.change;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.flag.FlagConfigJson;
import dev.flagtide.domain.json.Json;
import dev.flagtide.domain.json.JsonFields;
import java.util.List;

public final class ChangeJson {

  private static final String UPSERT = "upsert";
  private static final String REMOVE = "remove";
  private static final String FLAG = "flag";
  private static final String SEGMENT = "segment";

  private ChangeJson() {}

  public static JsonValue toJson(Change change) {
    return switch (change) {
      case Change.FlagUpserted upserted ->
          entry(UPSERT, FLAG, upserted.key())
              .put("config", FlagConfigJson.toJson(upserted.config()))
              .build();
      case Change.FlagRemoved removed -> entry(REMOVE, FLAG, removed.key()).build();
      case Change.SegmentUpserted upserted ->
          entry(UPSERT, SEGMENT, upserted.key())
              .put("config", FlagConfigJson.segmentToJson(upserted.segment()))
              .build();
      case Change.SegmentRemoved removed -> entry(REMOVE, SEGMENT, removed.key()).build();
    };
  }

  public static JsonValue toJson(List<Change> changes) {
    return Json.array(changes, ChangeJson::toJson);
  }

  public static Change fromJson(JsonValue json) {
    JsonFields fields = JsonFields.of("change", json);
    String operation = fields.text("op");
    String kind = fields.text("kind");
    String key = fields.text("key");
    return switch (operation + ":" + kind) {
      case UPSERT + ":" + FLAG ->
          new Change.FlagUpserted(FlagConfigJson.fromJson(fields.require("config")));
      case REMOVE + ":" + FLAG -> new Change.FlagRemoved(key);
      case UPSERT + ":" + SEGMENT ->
          new Change.SegmentUpserted(FlagConfigJson.segmentFromJson(fields.require("config")));
      case REMOVE + ":" + SEGMENT -> new Change.SegmentRemoved(key);
      default ->
          throw FlagtideException.invalid("change", "unknown change " + operation + " " + kind);
    };
  }

  public static List<Change> listFromJson(JsonValue json) {
    return Json.requireArray("changes", json).stream().map(ChangeJson::fromJson).toList();
  }

  private static Json.ObjectBuilder entry(String operation, String kind, String key) {
    return Json.object().text("op", operation).text("kind", kind).text("key", key);
  }
}
