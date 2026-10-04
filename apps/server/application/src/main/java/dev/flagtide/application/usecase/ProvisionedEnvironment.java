package dev.flagtide.application.usecase;

import dev.flagtide.domain.project.Environment;
import java.util.List;

public record ProvisionedEnvironment(Environment environment, List<IssuedKey> keys) {

  public ProvisionedEnvironment {
    keys = List.copyOf(keys);
  }
}
