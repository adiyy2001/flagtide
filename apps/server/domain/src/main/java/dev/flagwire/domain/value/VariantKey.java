package dev.flagwire.domain.value;

public record VariantKey(String value) implements Comparable<VariantKey> {

  public VariantKey {
    Slugs.require("variant", value);
  }

  public static VariantKey of(String value) {
    return new VariantKey(value);
  }

  @Override
  public int compareTo(VariantKey other) {
    return this.value.compareTo(other.value);
  }

  @Override
  public String toString() {
    return this.value;
  }
}
