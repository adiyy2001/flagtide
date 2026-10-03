package dev.flagwire.application.security;

import dev.flagwire.domain.access.ApiKey;
import dev.flagwire.domain.access.ApiKeyKind;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.ProjectKey;

public record Principal(
    String keyId, ApiKeyKind kind, ProjectKey project, EnvironmentKey environment, String label) {

  public static Principal of(ApiKey key) {
    return new Principal(key.id(), key.kind(), key.project(), key.environment(), key.label());
  }

  public String author() {
    return this.label;
  }

  public EnvironmentRef environmentRef() {
    return new EnvironmentRef(this.project, this.environment);
  }
}
