package dev.flagtide.domain.value;

import dev.flagtide.domain.error.FlagtideException;

public record Weight(int units) {

  public static final int TOTAL = 100_000;

  public Weight {
    if (units < 0 || units > TOTAL) {
      throw FlagtideException.invalid("weight", "must be between 0 and 100000 but was " + units);
    }
  }

  public static Weight of(int units) {
    return new Weight(units);
  }
}
