package dev.flagtide.domain.flag;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.value.RuleId;
import dev.flagtide.domain.value.Salt;
import dev.flagtide.domain.value.SegmentKey;
import dev.flagtide.domain.value.VariantKey;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public record FlagEnvironmentConfig(
    boolean enabled,
    boolean killSwitch,
    VariantKey offVariant,
    List<TargetingRule> rules,
    Serving fallthrough,
    Salt salt) {

  private static final int MAX_RULES = 100;

  public FlagEnvironmentConfig {
    rules = rules.stream().sorted(Comparator.comparingInt(TargetingRule::order)).toList();
    if (rules.size() > MAX_RULES) {
      throw FlagtideException.invalid("rules", "a flag can have at most 100 rules per environment");
    }
    requireContiguousOrder(rules);
    requireUniqueIds(rules);
  }

  public static FlagEnvironmentConfig initial(VariantKey offVariant, Salt salt) {
    return new FlagEnvironmentConfig(
        false, false, offVariant, List.of(), Serving.fixed(offVariant), salt);
  }

  public FlagEnvironmentConfig withEnabled(boolean value) {
    return new FlagEnvironmentConfig(
        value, this.killSwitch, this.offVariant, this.rules, this.fallthrough, this.salt);
  }

  public FlagEnvironmentConfig withKillSwitch(boolean value) {
    return new FlagEnvironmentConfig(
        this.enabled, value, this.offVariant, this.rules, this.fallthrough, this.salt);
  }

  public Set<VariantKey> servedVariants() {
    return Stream.concat(
            Stream.of(this.offVariant),
            Stream.concat(
                this.rules.stream().flatMap(rule -> rule.serving().variants().stream()),
                this.fallthrough.variants().stream()))
        .collect(Collectors.toSet());
  }

  public Set<SegmentKey> referencedSegments() {
    return this.rules.stream()
        .flatMap(rule -> rule.referencedSegments().stream())
        .collect(Collectors.toSet());
  }

  private static void requireContiguousOrder(List<TargetingRule> rules) {
    Set<Integer> seen = new HashSet<>();
    rules.forEach(
        rule -> {
          if (!seen.add(rule.order())) {
            throw FlagtideException.invalid(
                "rules", "order " + rule.order() + " is used by more than one rule");
          }
        });
    boolean contiguous = IntStream.range(0, rules.size()).allMatch(seen::contains);
    if (!contiguous) {
      throw FlagtideException.invalid("rules", "order must run from 0 without gaps");
    }
  }

  private static void requireUniqueIds(List<TargetingRule> rules) {
    Set<RuleId> seen = new HashSet<>();
    rules.forEach(
        rule -> {
          if (!seen.add(rule.id())) {
            throw FlagtideException.invalid(
                "rules", "rule id " + rule.id().value() + " is used by more than one rule");
          }
        });
  }
}
