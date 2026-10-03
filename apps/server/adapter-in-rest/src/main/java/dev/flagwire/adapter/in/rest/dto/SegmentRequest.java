package dev.flagwire.adapter.in.rest.dto;

import dev.flagwire.adapter.in.rest.Required;
import dev.flagwire.application.usecase.SaveSegment;
import dev.flagwire.domain.evaluation.Condition;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.Revision;
import dev.flagwire.domain.value.SegmentKey;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "SaveSegment")
public record SegmentRequest(
    String name, List<String> included, List<String> excluded, List<List<ConditionDto>> rules) {

  public SaveSegment.Command toCommand(
      EnvironmentKey environment, SegmentKey key, Optional<Revision> expected) {
    return new SaveSegment.Command(
        environment,
        key,
        expected,
        Required.text("name", this.name),
        new HashSet<>(Required.items("included", this.included)),
        new HashSet<>(Required.items("excluded", this.excluded)),
        Required.items("rules", this.rules).stream().map(SegmentRequest::group).toList());
  }

  private static List<Condition.Attribute> group(List<ConditionDto> group) {
    return Required.items("rules", group).stream()
        .map(condition -> condition.toAttribute("rules"))
        .toList();
  }
}
