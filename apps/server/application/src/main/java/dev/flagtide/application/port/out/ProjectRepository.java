package dev.flagtide.application.port.out;

import dev.flagtide.domain.project.Project;
import dev.flagtide.domain.value.ProjectKey;
import dev.flagtide.domain.value.Revision;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository {

  Optional<Project> find(ProjectKey key);

  List<Project> findAll();

  void save(Project project, Revision expected);
}
