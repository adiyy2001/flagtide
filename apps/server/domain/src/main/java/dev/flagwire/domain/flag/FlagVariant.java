package dev.flagwire.domain.flag;

import dev.flagwire.domain.evaluation.JsonValue;
import dev.flagwire.domain.value.VariantKey;

public record FlagVariant(VariantKey key, JsonValue value) {}
