package dev.flagtide.domain.value;

import dev.flagtide.domain.error.FlagtideException;

public record RuleId(String value) {

  private static final int MAX_LENGTH = 64;

  public RuleId {
    if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
      throw FlagtideException.invalid("rule id", "must be 1 to 64 characters and not blank");
    }
  }

  public static RuleId of(String value) {
    return new RuleId(value);
  }
}
