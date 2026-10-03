package dev.flagwire.domain.access;

import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.ProjectKey;
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
      throw FlagwireException.invalid("label", "must be 1 to 100 characters");
    }
    if (lookup.isBlank()) {
      throw FlagwireException.invalid("lookup", "must not be blank");
    }
  }
}
