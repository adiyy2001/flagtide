package dev.flagwire.application.propagation;

import dev.flagwire.application.port.out.TimeSource;
import java.util.Arrays;
import java.util.stream.IntStream;

public final class WindowedLatency {

  public static final int SLICE_SECONDS = 5;
  public static final int SLICE_COUNT = 12;

  private final TimeSource timeSource;
  private final LatencyHistogram[] slices = new LatencyHistogram[SLICE_COUNT];
  private final long[] sliceIds = new long[SLICE_COUNT];

  public WindowedLatency(TimeSource timeSource) {
    this.timeSource = timeSource;
    IntStream.range(0, SLICE_COUNT).forEach(index -> this.slices[index] = new LatencyHistogram());
    Arrays.fill(this.sliceIds, -1);
  }

  public synchronized void record(long latencyMillis) {
    long sliceId = this.currentSliceId();
    int index = (int) (sliceId % SLICE_COUNT);
    if (this.sliceIds[index] != sliceId) {
      this.slices[index].clear();
      this.sliceIds[index] = sliceId;
    }
    this.slices[index].record(latencyMillis);
  }

  public synchronized LatencyHistogram window() {
    long newest = this.currentSliceId();
    LatencyHistogram merged = new LatencyHistogram();
    IntStream.range(0, SLICE_COUNT)
        .filter(index -> this.sliceIds[index] > newest - SLICE_COUNT)
        .filter(index -> this.sliceIds[index] <= newest)
        .forEach(index -> merged.merge(this.slices[index]));
    return merged;
  }

  private long currentSliceId() {
    return this.timeSource.now().toEpochSecond() / SLICE_SECONDS;
  }
}
