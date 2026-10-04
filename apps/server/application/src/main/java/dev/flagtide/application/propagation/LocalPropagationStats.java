package dev.flagtide.application.propagation;

import dev.flagtide.application.port.out.PropagationReport;
import dev.flagtide.application.port.out.PropagationStats;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.domain.value.EnvironmentRef;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalPropagationStats implements PropagationStats {

  private static final class EnvironmentStats {

    private final AtomicInteger connected = new AtomicInteger();
    private final WindowedLatency latency;

    EnvironmentStats(TimeSource timeSource) {
      this.latency = new WindowedLatency(timeSource);
    }
  }

  private final TimeSource timeSource;
  private final Map<EnvironmentRef, EnvironmentStats> stats = new ConcurrentHashMap<>();

  public LocalPropagationStats(TimeSource timeSource) {
    this.timeSource = timeSource;
  }

  @Override
  public void clientConnected(EnvironmentRef environment) {
    this.statsOf(environment).connected.incrementAndGet();
  }

  @Override
  public void clientDisconnected(EnvironmentRef environment) {
    this.statsOf(environment).connected.updateAndGet(current -> Math.max(0, current - 1));
  }

  @Override
  public void recordAcknowledgement(EnvironmentRef environment, long latencyMillis) {
    this.statsOf(environment).latency.record(latencyMillis);
  }

  @Override
  public PropagationReport report(EnvironmentRef environment) {
    return this.window(environment).report(this.connectedClients(environment));
  }

  public Set<EnvironmentRef> environments() {
    return Set.copyOf(this.stats.keySet());
  }

  public int connectedClients(EnvironmentRef environment) {
    return this.statsOf(environment).connected.get();
  }

  public LatencyHistogram window(EnvironmentRef environment) {
    return this.statsOf(environment).latency.window();
  }

  private EnvironmentStats statsOf(EnvironmentRef environment) {
    return this.stats.computeIfAbsent(environment, key -> new EnvironmentStats(this.timeSource));
  }
}
