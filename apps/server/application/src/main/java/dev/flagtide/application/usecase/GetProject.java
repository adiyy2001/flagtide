package dev.flagtide.application.usecase;

import dev.flagtide.application.port.out.ProjectRepository;
import dev.flagtide.application.security.Authorizer;
import dev.flagtide.application.security.Principal;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.project.Project;

public final class GetProject {

  private final ProjectRepository projects;
  private final Authorizer authorizer;

  public GetProject(ProjectRepository projects, Authorizer authorizer) {
    this.projects = projects;
    this.authorizer = authorizer;
  }

  public Project execute(Principal principal) {
    this.authorizer.requireAdmin(principal);
    return this.projects
        .find(principal.project())
        .orElseThrow(() -> FlagtideException.notFound("project", principal.project().value()));
  }
}
