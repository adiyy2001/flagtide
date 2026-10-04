package dev.flagtide.application.usecase;

import dev.flagtide.domain.project.Project;
import java.util.List;

public record ProvisionedProject(Project project, List<IssuedKey> keys) {

  public ProvisionedProject {
    keys = List.copyOf(keys);
  }
}
