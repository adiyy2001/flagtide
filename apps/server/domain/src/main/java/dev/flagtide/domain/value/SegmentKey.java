package dev.flagtide.domain.value;

public record SegmentKey(String value) implements Comparable<SegmentKey> {

  public SegmentKey {
    Slugs.require("segment", value);
  }

  public static SegmentKey of(String value) {
    return new SegmentKey(value);
  }

  @Override
  public int compareTo(SegmentKey other) {
    return this.value.compareTo(other.value);
  }

  @Override
  public String toString() {
    return this.value;
  }
}
