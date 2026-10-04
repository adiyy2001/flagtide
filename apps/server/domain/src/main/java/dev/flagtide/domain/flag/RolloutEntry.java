package dev.flagtide.domain.flag;

import dev.flagtide.domain.value.VariantKey;
import dev.flagtide.domain.value.Weight;

public record RolloutEntry(VariantKey variant, Weight weight) {}
