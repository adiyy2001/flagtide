package dev.flagwire.application.stream;

import dev.flagwire.application.change.ChangeJson;
import dev.flagwire.application.change.ChangeLogEntry;
import dev.flagwire.application.sync.Snapshot;
import dev.flagwire.domain.evaluation.JsonValue;
import dev.flagwire.domain.flag.FlagConfigJson;
import dev.flagwire.domain.json.Json;
import dev.flagwire.domain.json.JsonText;
import java.time.ZonedDateTime;
import java.util.List;

public final class Frames {

  private Frames() {}

  public static String snapshot(Snapshot snapshot) {
    Json.ObjectBuilder frame =
        Json.object()
            .text("t", "snapshot")
            .number("v", snapshot.version().value())
            .put("flags", Json.array(snapshot.flags(), FlagConfigJson::toJson))
            .put("segments", Json.array(snapshot.segments(), FlagConfigJson::segmentToJson));
    snapshot.committedAt().ifPresent(time -> frame.number("committedAtMs", epochMillis(time)));
    return JsonText.write(frame.build());
  }

  public static String deltas(long from, long to, List<ChangeLogEntry> entries) {
    return JsonText.write(
        Json.object()
            .text("t", "deltas")
            .number("from", from)
            .number("to", to)
            .put("entries", Json.array(entries, Frames::entry))
            .build());
  }

  public static String heartbeat(ZonedDateTime now, long version) {
    return JsonText.write(
        Json.object().text("t", "hb").number("ts", epochMillis(now)).number("v", version).build());
  }

  public static String error(int code, String message) {
    return JsonText.write(
        Json.object().text("t", "error").number("code", code).text("message", message).build());
  }

  public static long epochMillis(ZonedDateTime time) {
    return time.toInstant().toEpochMilli();
  }

  private static JsonValue entry(ChangeLogEntry entry) {
    return Json.object()
        .number("v", entry.version().value())
        .number("committedAtMs", epochMillis(entry.committedAt()))
        .put("changes", ChangeJson.toJson(entry.changes()))
        .build();
  }
}
