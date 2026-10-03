package dev.flagwire.domain.project;

import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.value.EnvironmentKey;

public record Environment(EnvironmentKey key, String name) {

  public Environment {
    if (name.isBlank() || name.length() > 100) {
      throw FlagwireException.invalid("environment name", "must be 1 to 100 characters");
    }
  }
}
