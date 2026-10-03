package dev.flagwire.application.propagation;

import java.util.Arrays;

public final class LatencyHistogram {

  public static final int MAX_TRACKED_MILLIS = 5000;
  public static final int OVERFLOW_MILLIS = MAX_TRACKED_MILLIS + 1;

  private final long[] buckets = new long[OVERFLOW_MILLIS + 1];

  public void record(long latencyMillis) {
    int index = (int) Math.min(Math.max(latencyMillis, 0), OVERFLOW_MILLIS);
    this.buckets[index]++;
  }

  public void merge(LatencyHistogram other) {
    for (int index = 0; index < this.buckets.length; index++) {
      this.buckets[index] += other.buckets[index];
    }
  }

  public void clear() {
    Arrays.fill(this.buckets, 0);
  }

  public long count() {
    return Arrays.stream(this.buckets).sum();
  }

  public long percentile(double fraction) {
    long total = this.count();
    if (total == 0) {
      return 0;
    }
    long target = (long) Math.ceil(fraction * total);
    long running = 0;
    for (int index = 0; index < this.buckets.length; index++) {
      running += this.buckets[index];
      if (running >= Math.max(target, 1)) {
        return index;
      }
    }
    return OVERFLOW_MILLIS;
  }
}
