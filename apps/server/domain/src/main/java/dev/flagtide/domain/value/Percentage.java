package dev.flagtide.domain.value;

import dev.flagtide.domain.error.FlagtideException;

public record Percentage(int units) {

  public Percentage {
    if (units < 0 || units > Weight.TOTAL) {
      throw FlagtideException.invalid(
          "percentage", "must be between 0 and 100000 but was " + units);
    }
  }

  public static Percentage of(int units) {
    return new Percentage(units);
  }

  public Percentage complement() {
    return new Percentage(Weight.TOTAL - this.units);
  }

  public Weight toWeight() {
    return new Weight(this.units);
  }

  public double asPercent() {
    return this.units / 1000.0;
  }
}
