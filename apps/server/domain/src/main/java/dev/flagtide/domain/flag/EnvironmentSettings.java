package dev.flagtide.domain.flag;

import dev.flagtide.domain.value.VariantKey;
import java.util.List;

public record EnvironmentSettings(
    boolean enabled, VariantKey offVariant, List<TargetingRule> rules, Serving fallthrough) {

  public EnvironmentSettings {
    rules = List.copyOf(rules);
  }
}
