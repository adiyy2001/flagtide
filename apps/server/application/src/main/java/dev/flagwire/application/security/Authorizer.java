package dev.flagwire.application.security;

import dev.flagwire.domain.access.ApiKeyKind;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.value.EnvironmentKey;

public final class Authorizer {

  public void requireAdmin(Principal principal) {
    if (principal.kind() != ApiKeyKind.ADMIN) {
      throw FlagwireException.forbidden("this operation needs an admin key");
    }
  }

  public void requireEnvironmentWrite(Principal principal, EnvironmentKey environment) {
    this.requireAdmin(principal);
    if (!principal.environment().equals(environment)) {
      throw FlagwireException.forbidden(
          "the key belongs to environment "
              + principal.environment().value()
              + " and cannot change "
              + environment.value());
    }
  }

  public void requireEnvironmentRead(Principal principal, EnvironmentKey environment) {
    if (!principal.environment().equals(environment)) {
      throw FlagwireException.forbidden(
          "the key belongs to environment "
              + principal.environment().value()
              + " and cannot read "
              + environment.value());
    }
  }
}
