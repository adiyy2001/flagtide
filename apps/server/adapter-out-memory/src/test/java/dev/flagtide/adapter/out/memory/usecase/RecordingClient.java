package dev.flagtide.adapter.out.memory.usecase;

import dev.flagtide.application.stream.StreamClient;
import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.json.JsonFields;
import dev.flagtide.domain.json.JsonText;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class RecordingClient implements StreamClient {

  private final List<String> frames = new CopyOnWriteArrayList<>();

  @Override
  public void send(String frame) {
    this.frames.add(frame);
  }

  List<String> frames() {
    return List.copyOf(this.frames);
  }

  String last() {
    return this.frames.getLast();
  }

  JsonFields lastFields() {
    return fields(this.last());
  }

  List<String> types() {
    return this.frames.stream().map(frame -> fields(frame).text("t")).toList();
  }

  static JsonFields fields(String frame) {
    return JsonFields.of("frame", JsonText.parse(frame));
  }

  static List<Long> entryVersions(JsonFields deltas) {
    return deltas.array("entries").stream()
        .map(entry -> JsonFields.of("entry", entry).integer("v"))
        .toList();
  }

  static List<String> keys(List<JsonValue> items) {
    return items.stream().map(item -> JsonFields.of("item", item).text("key")).toList();
  }
}
