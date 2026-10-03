package dev.flagwire.application.port.out;

import dev.flagwire.application.propagation.LatencyHistogram;

public record PropagationReport(
    int connectedClients, long samples, long p50Millis, long p95Millis, long p99Millis) {

  public static PropagationReport of(int connectedClients, LatencyHistogram latencies) {
    if (latencies.count() == 0) {
      return empty(connectedClients);
    }
    return new PropagationReport(
        connectedClients,
        latencies.count(),
        latencies.percentile(0.50),
        latencies.percentile(0.95),
        latencies.percentile(0.99));
  }

  public static PropagationReport empty(int connectedClients) {
    return new PropagationReport(connectedClients, 0, 0, 0, 0);
  }
}
