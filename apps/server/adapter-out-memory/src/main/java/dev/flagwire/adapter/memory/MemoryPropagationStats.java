package dev.flagwire.adapter.memory;

import dev.flagwire.application.port.out.PropagationReport;
import dev.flagwire.application.port.out.PropagationStats;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.propagation.LatencyHistogram;
import dev.flagwire.domain.value.EnvironmentRef;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

public final class MemoryPropagationStats implements PropagationStats {

  private static final int SLICE_SECONDS = 5;
  private static final int SLICE_COUNT = 12;

  private static final class EnvironmentStats {

    private int connected;
    private final LatencyHistogram[] slices = new LatencyHistogram[SLICE_COUNT];
    private final long[] sliceIds = new long[SLICE_COUNT];

    EnvironmentStats() {
      IntStream.range(0, SLICE_COUNT).forEach(index -> this.slices[index] = new LatencyHistogram());
      Arrays.fill(this.sliceIds, -1);
    }
  }

  private final TimeSource timeSource;
  private final Map<EnvironmentRef, EnvironmentStats> stats = new HashMap<>();

  public MemoryPropagationStats(TimeSource timeSource) {
    this.timeSource = timeSource;
  }

  @Override
  public synchronized void clientConnected(EnvironmentRef environment) {
    this.statsOf(environment).connected++;
  }

  @Override
  public synchronized void clientDisconnected(EnvironmentRef environment) {
    EnvironmentStats current = this.statsOf(environment);
    current.connected = Math.max(0, current.connected - 1);
  }

  @Override
  public synchronized void recordAcknowledgement(EnvironmentRef environment, long latencyMillis) {
    EnvironmentStats current = this.statsOf(environment);
    long sliceId = this.currentSliceId();
    int index = (int) (sliceId % SLICE_COUNT);
    if (current.sliceIds[index] != sliceId) {
      current.slices[index].clear();
      current.sliceIds[index] = sliceId;
    }
    current.slices[index].record(latencyMillis);
  }

  @Override
  public synchronized PropagationReport report(EnvironmentRef environment) {
    EnvironmentStats current = this.statsOf(environment);
    long newest = this.currentSliceId();
    LatencyHistogram merged = new LatencyHistogram();
    IntStream.range(0, SLICE_COUNT)
        .filter(index -> current.sliceIds[index] > newest - SLICE_COUNT)
        .filter(index -> current.sliceIds[index] <= newest)
        .forEach(index -> merged.merge(current.slices[index]));
    if (merged.count() == 0) {
      return PropagationReport.empty(current.connected);
    }
    return new PropagationReport(
        current.connected,
        merged.count(),
        merged.percentile(0.50),
        merged.percentile(0.95),
        merged.percentile(0.99));
  }

  private EnvironmentStats statsOf(EnvironmentRef environment) {
    return this.stats.computeIfAbsent(environment, key -> new EnvironmentStats());
  }

  private long currentSliceId() {
    return this.timeSource.now().toEpochSecond() / SLICE_SECONDS;
  }
}
