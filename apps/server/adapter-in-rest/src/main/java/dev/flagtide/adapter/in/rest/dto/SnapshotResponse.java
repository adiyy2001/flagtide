package dev.flagtide.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import dev.flagtide.adapter.in.rest.JsonNodes;
import dev.flagtide.application.sync.Snapshot;
import dev.flagtide.domain.flag.FlagConfigJson;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Snapshot", description = "Every active flag and segment of one environment")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SnapshotResponse(
    @Schema(description = "Environment version this snapshot reflects") long v,
    @Schema(description = "Commit time of that version in epoch milliseconds, absent at version 0")
        Long committedAtMs,
    List<JsonNode> flags,
    List<JsonNode> segments) {

  public static SnapshotResponse from(Snapshot snapshot) {
    return new SnapshotResponse(
        snapshot.version().value(),
        snapshot.committedAt().map(time -> time.toInstant().toEpochMilli()).orElse(null),
        snapshot.flags().stream()
            .map(config -> JsonNodes.toNode(FlagConfigJson.toJson(config)))
            .toList(),
        snapshot.segments().stream()
            .map(segment -> JsonNodes.toNode(FlagConfigJson.segmentToJson(segment)))
            .toList());
  }
}
