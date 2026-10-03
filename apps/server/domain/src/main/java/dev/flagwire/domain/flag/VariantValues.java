package dev.flagwire.domain.flag;

import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.evaluation.FlagType;
import dev.flagwire.domain.evaluation.JsonValue;

final class VariantValues {

  private VariantValues() {}

  static void requireFits(FlagType type, FlagVariant variant) {
    if (!fits(type, variant.value())) {
      throw FlagwireException.invalid(
          "variants." + variant.key().value(),
          "value does not match the flag type " + type.name().toLowerCase());
    }
  }

  private static boolean fits(FlagType type, JsonValue value) {
    return switch (type) {
      case BOOLEAN -> value instanceof JsonValue.JsonBoolean;
      case STRING -> value instanceof JsonValue.JsonString;
      case NUMBER ->
          value instanceof JsonValue.JsonNumber number && Double.isFinite(number.value());
      case JSON -> value instanceof JsonValue.JsonObject || value instanceof JsonValue.JsonArray;
    };
  }
}
