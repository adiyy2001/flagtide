package dev.flagwire.domain.value;

import dev.flagwire.domain.error.FlagwireException;

public record RuleId(String value) {

  private static final int MAX_LENGTH = 64;

  public RuleId {
    if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
      throw FlagwireException.invalid("rule id", "must be 1 to 64 characters and not blank");
    }
  }

  public static RuleId of(String value) {
    return new RuleId(value);
  }
}
