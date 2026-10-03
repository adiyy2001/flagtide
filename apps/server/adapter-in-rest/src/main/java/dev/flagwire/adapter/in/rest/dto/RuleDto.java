package dev.flagwire.adapter.in.rest.dto;

import dev.flagwire.adapter.in.rest.Required;
import dev.flagwire.domain.flag.TargetingRule;
import dev.flagwire.domain.value.RuleId;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Rule")
public record RuleDto(String id, Integer order, List<ConditionDto> conditions, ServeDto serve) {

  public static RuleDto from(TargetingRule rule) {
    return new RuleDto(
        rule.id().value(),
        rule.order(),
        rule.conditions().stream().map(ConditionDto::from).toList(),
        ServeDto.from(rule.serving()));
  }

  public TargetingRule toDomain(String field) {
    return new TargetingRule(
        new RuleId(Required.text(field + ".id", this.id)),
        Required.field(field + ".order", this.order),
        Required.items(field + ".conditions", this.conditions).stream()
            .map(condition -> condition.toDomain(field + ".conditions"))
            .toList(),
        Required.field(field + ".serve", this.serve).toDomain(field + ".serve"));
  }
}
