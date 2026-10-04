package dev.flagtide.domain.access;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.ProjectKey;
import java.time.ZonedDateTime;

public record ApiKey(
    String id,
    ApiKeyKind kind,
    ProjectKey project,
    EnvironmentKey environment,
    String label,
    String lookup,
    ZonedDateTime createdAt) {

  public ApiKey {
    if (label.isBlank() || label.length() > 100) {
      throw FlagtideException.invalid("label", "must be 1 to 100 characters");
    }
    if (lookup.isBlank()) {
      throw FlagtideException.invalid("lookup", "must not be blank");
    }
  }
}
