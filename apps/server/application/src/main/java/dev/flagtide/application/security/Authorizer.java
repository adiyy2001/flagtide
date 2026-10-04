package dev.flagtide.application.security;

import dev.flagtide.domain.access.ApiKeyKind;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.value.EnvironmentKey;

public final class Authorizer {

  public void requireAdmin(Principal principal) {
    if (principal.kind() != ApiKeyKind.ADMIN) {
      throw FlagtideException.forbidden("this operation needs an admin key");
    }
  }

  public void requireEnvironmentWrite(Principal principal, EnvironmentKey environment) {
    this.requireAdmin(principal);
    if (!principal.environment().equals(environment)) {
      throw FlagtideException.forbidden(
          "the key belongs to environment "
              + principal.environment().value()
              + " and cannot change "
              + environment.value());
    }
  }

  public void requireEnvironmentRead(Principal principal, EnvironmentKey environment) {
    if (!principal.environment().equals(environment)) {
      throw FlagtideException.forbidden(
          "the key belongs to environment "
              + principal.environment().value()
              + " and cannot read "
              + environment.value());
    }
  }
}
