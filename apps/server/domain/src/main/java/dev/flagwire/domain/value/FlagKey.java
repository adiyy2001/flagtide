package dev.flagwire.domain.value;

public record FlagKey(String value) implements Comparable<FlagKey> {

  public FlagKey {
    Slugs.require("flag", value);
  }

  public static FlagKey of(String value) {
    return new FlagKey(value);
  }

  @Override
  public int compareTo(FlagKey other) {
    return this.value.compareTo(other.value);
  }

  @Override
  public String toString() {
    return this.value;
  }
}
