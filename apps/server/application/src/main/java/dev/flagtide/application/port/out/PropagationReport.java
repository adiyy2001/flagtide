package dev.flagtide.application.port.out;

public record PropagationReport(
    int connectedClients, long samples, long p50Millis, long p95Millis, long p99Millis) {

  public static PropagationReport empty(int connectedClients) {
    return new PropagationReport(connectedClients, 0, 0, 0, 0);
  }
}
