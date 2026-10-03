package dev.flagwire.application.usecase;

import dev.flagwire.application.port.out.ProjectRepository;
import dev.flagwire.application.port.out.PropagationStats;
import dev.flagwire.application.security.Authorizer;
import dev.flagwire.application.security.Principal;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.EnvironmentRef;

public final class GetPropagation {

  public record Report(
      int connectedClients, long samples, long p50Millis, long p95Millis, long p99Millis) {}

  private final ProjectRepository projects;
  private final PropagationStats stats;
  private final Authorizer authorizer;

  public GetPropagation(ProjectRepository projects, PropagationStats stats, Authorizer authorizer) {
    this.projects = projects;
    this.stats = stats;
    this.authorizer = authorizer;
  }

  public Report execute(Principal principal, EnvironmentKey environment) {
    this.authorizer.requireAdmin(principal);
    this.projects
        .find(principal.project())
        .flatMap(project -> project.environment(environment))
        .orElseThrow(() -> FlagwireException.notFound("environment", environment.value()));
    var report = this.stats.report(new EnvironmentRef(principal.project(), environment));
    return new Report(
        report.connectedClients(),
        report.samples(),
        report.p50Millis(),
        report.p95Millis(),
        report.p99Millis());
  }
}
