package dev.flagtide.adapter.out.memory;

import dev.flagtide.application.port.out.ProjectRepository;
import dev.flagtide.domain.project.Project;
import dev.flagtide.domain.value.ProjectKey;
import dev.flagtide.domain.value.Revision;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MemoryProjectRepository implements ProjectRepository {

  private final MemoryDatabase database;
  private final Map<ProjectKey, Project> projects = new HashMap<>();

  public MemoryProjectRepository(MemoryDatabase database) {
    this.database = database;
  }

  @Override
  public Optional<Project> find(ProjectKey key) {
    return this.database.read(() -> Optional.ofNullable(this.projects.get(key)));
  }

  @Override
  public List<Project> findAll() {
    return this.database.read(
        () -> this.projects.values().stream().sorted(Comparator.comparing(Project::key)).toList());
  }

  @Override
  public void save(Project project, Revision expected) {
    this.database.write(
        () -> {
          Optional<Project> previous = Optional.ofNullable(this.projects.get(project.key()));
          Revisions.requireMatch(
              "project", project.key().value(), previous.map(Project::revision), expected);
          this.projects.put(project.key(), project);
          this.database.onRollback(
              () ->
                  previous.ifPresentOrElse(
                      old -> this.projects.put(project.key(), old),
                      () -> this.projects.remove(project.key())));
          return null;
        });
  }
}
