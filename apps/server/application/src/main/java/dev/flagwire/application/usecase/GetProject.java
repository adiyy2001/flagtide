package dev.flagwire.application.usecase;

import dev.flagwire.application.port.out.ProjectRepository;
import dev.flagwire.application.security.Authorizer;
import dev.flagwire.application.security.Principal;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.project.Project;

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
        .orElseThrow(() -> FlagwireException.notFound("project", principal.project().value()));
  }
}
