package dev.flagwire.application.usecase;

import dev.flagwire.domain.project.Environment;
import java.util.List;

public record ProvisionedEnvironment(Environment environment, List<IssuedKey> keys) {

  public ProvisionedEnvironment {
    keys = List.copyOf(keys);
  }
}
