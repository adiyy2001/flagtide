package dev.flagtide.application.port.out;

import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.ProjectKey;
import java.util.Optional;

public record AuditQuery(
    ProjectKey project,
    Optional<EnvironmentKey> environment,
    Optional<String> entityKey,
    int limit,
    int offset) {

  public static AuditQuery forProject(ProjectKey project, int limit) {
    return new AuditQuery(project, Optional.empty(), Optional.empty(), limit, 0);
  }

  public AuditQuery inEnvironment(EnvironmentKey key) {
    return new AuditQuery(this.project, Optional.of(key), this.entityKey, this.limit, this.offset);
  }

  public AuditQuery aboutEntity(String key) {
    return new AuditQuery(
        this.project, this.environment, Optional.of(key), this.limit, this.offset);
  }

  public AuditQuery skipping(int skipped) {
    return new AuditQuery(this.project, this.environment, this.entityKey, this.limit, skipped);
  }
}
