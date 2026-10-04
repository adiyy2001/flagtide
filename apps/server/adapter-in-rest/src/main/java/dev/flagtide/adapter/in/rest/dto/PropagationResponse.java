package dev.flagtide.adapter.in.rest.dto;

import dev.flagtide.application.usecase.GetPropagation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
    name = "Propagation",
    description =
        "Change delivery latency in milliseconds over the last minute, merged across instances")
public record PropagationResponse(
    int connectedClients, long samples, long p50Millis, long p95Millis, long p99Millis) {

  public static PropagationResponse from(GetPropagation.Report report) {
    return new PropagationResponse(
        report.connectedClients(),
        report.samples(),
        report.p50Millis(),
        report.p95Millis(),
        report.p99Millis());
  }
}
