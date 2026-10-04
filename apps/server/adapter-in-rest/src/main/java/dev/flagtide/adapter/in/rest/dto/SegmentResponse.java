package dev.flagtide.adapter.in.rest.dto;

import dev.flagtide.domain.segment.Segment;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Segment")
public record SegmentResponse(
    String environment,
    String key,
    String name,
    List<String> included,
    List<String> excluded,
    List<List<ConditionDto>> rules,
    long revision,
    String createdAt,
    String updatedAt) {

  public static SegmentResponse from(Segment segment) {
    return new SegmentResponse(
        segment.environment().value(),
        segment.key().value(),
        segment.name(),
        segment.included().stream().sorted().toList(),
        segment.excluded().stream().sorted().toList(),
        segment.rules().stream()
            .map(group -> group.stream().map(ConditionDto::from).toList())
            .toList(),
        segment.revision().value(),
        segment.createdAt().toString(),
        segment.updatedAt().toString());
  }
}
