package dev.flagwire.domain.evaluation;

import java.util.List;

public record Rule(String id, List<Condition> conditions, Serve serve) {
  public Rule {
    conditions = List.copyOf(conditions);
  }
}
