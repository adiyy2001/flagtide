package dev.flagtide.domain.flag;

import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.value.VariantKey;

public record FlagVariant(VariantKey key, JsonValue value) {}
