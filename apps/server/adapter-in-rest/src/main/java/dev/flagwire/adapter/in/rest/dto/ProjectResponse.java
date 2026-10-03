package dev.flagwire.adapter.in.rest.dto;

import dev.flagwire.domain.project.Environment;
import dev.flagwire.domain.project.Project;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Project")
public record ProjectResponse(
    String key,
    String name,
    long revision,
    String createdAt,
    List<EnvironmentResponse> environments) {

  @Schema(name = "Environment")
  public record EnvironmentResponse(String key, String name) {

    public static EnvironmentResponse from(Environment environment) {
      return new EnvironmentResponse(environment.key().value(), environment.name());
    }
  }

  public static ProjectResponse from(Project project) {
    return new ProjectResponse(
        project.key().value(),
        project.name(),
        project.revision().value(),
        project.createdAt().toString(),
        project.environments().stream().map(EnvironmentResponse::from).toList());
  }
}
