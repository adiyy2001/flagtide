package dev.flagtide.domain.value;

public record EnvironmentKey(String value) implements Comparable<EnvironmentKey> {

  public EnvironmentKey {
    Slugs.require("environment", value);
  }

  public static EnvironmentKey of(String value) {
    return new EnvironmentKey(value);
  }

  @Override
  public int compareTo(EnvironmentKey other) {
    return this.value.compareTo(other.value);
  }

  @Override
  public String toString() {
    return this.value;
  }
}
