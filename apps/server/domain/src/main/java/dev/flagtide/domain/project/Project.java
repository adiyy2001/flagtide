package dev.flagtide.domain.project;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.event.DomainEvent;
import dev.flagtide.domain.event.Stamp;
import dev.flagtide.domain.event.Transition;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.ProjectKey;
import dev.flagtide.domain.value.Revision;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record Project(
    ProjectKey key,
    String name,
    List<Environment> environments,
    Revision revision,
    ZonedDateTime createdAt) {

  public Project {
    environments = List.copyOf(environments);
    if (name.isBlank() || name.length() > 100) {
      throw FlagtideException.invalid("project name", "must be 1 to 100 characters");
    }
    requireDistinctEnvironments(environments);
    if (revision.compareTo(Revision.FIRST) < 0) {
      throw FlagtideException.invalid("revision", "a stored project has a revision of at least 1");
    }
  }

  public static Transition<Project> create(
      ProjectKey key, String name, List<Environment> environments, Stamp stamp) {
    Project created = new Project(key, name, environments, Revision.FIRST, stamp.occurredAt());
    return Transition.of(created, new DomainEvent.ProjectCreated(stamp, key));
  }

  public Optional<Environment> environment(EnvironmentKey environment) {
    return this.environments.stream()
        .filter(candidate -> candidate.key().equals(environment))
        .findFirst();
  }

  public Set<EnvironmentKey> environmentKeys() {
    Set<EnvironmentKey> keys = new HashSet<>();
    this.environments.forEach(environment -> keys.add(environment.key()));
    return keys;
  }

  public Transition<Project> addEnvironment(Environment environment, Stamp stamp) {
    if (this.environment(environment.key()).isPresent()) {
      throw FlagtideException.conflict(
          "environment " + environment.key().value() + " already exists");
    }
    List<Environment> extended = new ArrayList<>(this.environments);
    extended.add(environment);
    Project next = new Project(this.key, this.name, extended, this.revision.next(), this.createdAt);
    return Transition.of(
        next, new DomainEvent.EnvironmentCreated(stamp, this.key, environment.key()));
  }

  private static void requireDistinctEnvironments(List<Environment> environments) {
    if (environments.isEmpty()) {
      throw FlagtideException.invalid("environments", "a project needs at least one environment");
    }
    Set<EnvironmentKey> seen = new HashSet<>();
    environments.forEach(
        environment -> {
          if (!seen.add(environment.key())) {
            throw FlagtideException.invalid(
                "environments", "environment " + environment.key().value() + " is listed twice");
          }
        });
  }
}
