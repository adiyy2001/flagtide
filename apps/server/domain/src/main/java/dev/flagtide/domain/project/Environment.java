package dev.flagtide.domain.project;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.value.EnvironmentKey;

public record Environment(EnvironmentKey key, String name) {

  public Environment {
    if (name.isBlank() || name.length() > 100) {
      throw FlagtideException.invalid("environment name", "must be 1 to 100 characters");
    }
  }
}
