package dev.flagwire.application.port.out;

import dev.flagwire.domain.project.Project;
import dev.flagwire.domain.value.ProjectKey;
import dev.flagwire.domain.value.Revision;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository {

  Optional<Project> find(ProjectKey key);

  List<Project> findAll();

  void save(Project project, Revision expected);
}
