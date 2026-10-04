package dev.flagtide.domain.flag;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.value.Percentage;
import dev.flagtide.domain.value.VariantKey;
import dev.flagtide.domain.value.Weight;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public sealed interface Serving {

  Set<VariantKey> variants();

  static Serving fixed(VariantKey variant) {
    return new Fixed(variant);
  }

  record Fixed(VariantKey variant) implements Serving {
    @Override
    public Set<VariantKey> variants() {
      return Set.of(this.variant);
    }
  }

  record Rollout(List<RolloutEntry> entries) implements Serving {

    public Rollout {
      entries = List.copyOf(entries);
      requireDistinctVariants(entries);
      requireFullCoverage(entries);
    }

    public static Rollout split(VariantKey on, VariantKey off, Percentage onShare) {
      return new Rollout(
          List.of(
              new RolloutEntry(on, onShare.toWeight()),
              new RolloutEntry(off, onShare.complement().toWeight())));
    }

    @Override
    public Set<VariantKey> variants() {
      return this.entries.stream().map(RolloutEntry::variant).collect(Collectors.toSet());
    }

    private static void requireDistinctVariants(List<RolloutEntry> entries) {
      if (entries.isEmpty()) {
        throw FlagtideException.invalid("rollout", "must have at least one entry");
      }
      Set<VariantKey> seen = new HashSet<>();
      entries.forEach(
          entry -> {
            if (!seen.add(entry.variant())) {
              throw FlagtideException.invalid(
                  "rollout", "variant " + entry.variant().value() + " appears more than once");
            }
          });
    }

    private static void requireFullCoverage(List<RolloutEntry> entries) {
      long sum = entries.stream().mapToLong(entry -> entry.weight().units()).sum();
      if (sum != Weight.TOTAL) {
        throw FlagtideException.invalid(
            "rollout", "weights must sum to exactly 100000 but sum to " + sum);
      }
    }
  }
}
