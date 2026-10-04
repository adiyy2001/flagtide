package dev.flagtide.domain.value;

import dev.flagtide.domain.error.FlagtideException;

public record EnvironmentVersion(long value) implements Comparable<EnvironmentVersion> {

  public static final EnvironmentVersion ZERO = new EnvironmentVersion(0);

  public EnvironmentVersion {
    if (value < 0) {
      throw FlagtideException.invalid("version", "must not be negative");
    }
  }

  public static EnvironmentVersion of(long value) {
    return new EnvironmentVersion(value);
  }

  public EnvironmentVersion next() {
    return new EnvironmentVersion(this.value + 1);
  }

  public boolean isAfter(EnvironmentVersion other) {
    return this.value > other.value;
  }

  @Override
  public int compareTo(EnvironmentVersion other) {
    return Long.compare(this.value, other.value);
  }
}
