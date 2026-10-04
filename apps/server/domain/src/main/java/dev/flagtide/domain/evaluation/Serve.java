package dev.flagtide.domain.evaluation;

import java.util.List;

public sealed interface Serve {

  record Single(String variant) implements Serve {}

  record Rollout(List<WeightedVariant> entries) implements Serve {
    public Rollout {
      entries = List.copyOf(entries);
    }
  }
}
