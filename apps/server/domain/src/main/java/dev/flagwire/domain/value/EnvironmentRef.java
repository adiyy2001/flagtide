package dev.flagwire.domain.value;

public record EnvironmentRef(ProjectKey project, EnvironmentKey environment) {

  public static EnvironmentRef of(ProjectKey project, EnvironmentKey environment) {
    return new EnvironmentRef(project, environment);
  }
}
