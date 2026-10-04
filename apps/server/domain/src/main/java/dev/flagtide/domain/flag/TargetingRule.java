package dev.flagtide.domain.flag;

import dev.flagtide.domain.condition.ConditionRules;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.Condition;
import dev.flagtide.domain.value.RuleId;
import dev.flagtide.domain.value.SegmentKey;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record TargetingRule(RuleId id, int order, List<Condition> conditions, Serving serving) {

  private static final int MAX_CONDITIONS = 50;

  public TargetingRule {
    conditions = List.copyOf(conditions);
    if (order < 0) {
      throw FlagtideException.invalid("rules", "order must not be negative");
    }
    if (conditions.size() > MAX_CONDITIONS) {
      throw FlagtideException.invalid("rules", "a rule can have at most 50 conditions");
    }
    conditions.forEach(ConditionRules::validate);
  }

  public Set<SegmentKey> referencedSegments() {
    return this.conditions.stream()
        .flatMap(condition -> ConditionRules.referencedSegment(condition).stream())
        .collect(Collectors.toSet());
  }
}
