package dev.flagtide.domain.value;

import dev.flagtide.domain.error.FlagtideException;

public record Revision(long value) implements Comparable<Revision> {

  public static final Revision NONE = new Revision(0);
  public static final Revision FIRST = new Revision(1);

  public Revision {
    if (value < 0) {
      throw FlagtideException.invalid("revision", "must not be negative");
    }
  }

  public static Revision of(long value) {
    return new Revision(value);
  }

  public Revision next() {
    return new Revision(this.value + 1);
  }

  @Override
  public int compareTo(Revision other) {
    return Long.compare(this.value, other.value);
  }
}
