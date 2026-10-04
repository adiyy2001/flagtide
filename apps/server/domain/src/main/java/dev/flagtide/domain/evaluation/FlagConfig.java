package dev.flagtide.domain.evaluation;

import java.util.List;

public record FlagConfig(
    String key,
    FlagType type,
    boolean enabled,
    boolean killSwitch,
    String salt,
    List<Variant> variants,
    String offVariant,
    List<Rule> rules,
    Serve fallthrough) {
  public FlagConfig {
    variants = List.copyOf(variants);
    rules = List.copyOf(rules);
  }
}
