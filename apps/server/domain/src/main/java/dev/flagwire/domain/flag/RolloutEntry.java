package dev.flagwire.domain.flag;

import dev.flagwire.domain.value.VariantKey;
import dev.flagwire.domain.value.Weight;

public record RolloutEntry(VariantKey variant, Weight weight) {}
