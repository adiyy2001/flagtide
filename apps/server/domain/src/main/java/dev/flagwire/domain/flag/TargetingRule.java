package dev.flagwire.domain.flag;

import dev.flagwire.domain.condition.ConditionRules;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.evaluation.Condition;
import dev.flagwire.domain.value.RuleId;
import dev.flagwire.domain.value.SegmentKey;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record TargetingRule(RuleId id, int order, List<Condition> conditions, Serving serving) {

  private static final int MAX_CONDITIONS = 50;

  public TargetingRule {
    conditions = List.copyOf(conditions);
    if (order < 0) {
      throw FlagwireException.invalid("rules", "order must not be negative");
    }
    if (conditions.size() > MAX_CONDITIONS) {
      throw FlagwireException.invalid("rules", "a rule can have at most 50 conditions");
    }
    conditions.forEach(ConditionRules::validate);
  }

  public Set<SegmentKey> referencedSegments() {
    return this.conditions.stream()
        .flatMap(condition -> ConditionRules.referencedSegment(condition).stream())
        .collect(Collectors.toSet());
  }
}
