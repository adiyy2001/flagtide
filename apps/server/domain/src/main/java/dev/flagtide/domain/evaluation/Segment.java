package dev.flagtide.domain.evaluation;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record Segment(
    String key, Set<String> included, Set<String> excluded, List<List<Condition.Attribute>> rules) {
  public Segment {
    included = Set.copyOf(included);
    excluded = Set.copyOf(excluded);
    rules = rules.stream().map(List::copyOf).toList();
  }

  public static Map<String, Segment> indexByKey(List<Segment> segments) {
    return segments.stream()
        .collect(Collectors.toUnmodifiableMap(Segment::key, Function.identity()));
  }
}
