package dev.flagtide.domain.flag;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.FlagType;
import dev.flagtide.domain.evaluation.JsonValue;

final class VariantValues {

  private VariantValues() {}

  static void requireFits(FlagType type, FlagVariant variant) {
    if (!fits(type, variant.value())) {
      throw FlagtideException.invalid(
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
