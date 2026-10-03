package dev.flagwire.adapter.in.rest;

import dev.flagwire.application.security.Principal;
import dev.flagwire.domain.error.FlagwireException;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class Caller {

  private Principal principal;

  void authenticated(Principal authenticated) {
    this.principal = authenticated;
  }

  public Principal principal() {
    if (this.principal == null) {
      throw FlagwireException.unauthorized("a bearer key is required");
    }
    return this.principal;
  }

  public Principal inProject(String project) {
    Principal current = this.principal();
    if (!current.project().value().equals(project)) {
      throw FlagwireException.forbidden("the key belongs to another project");
    }
    return current;
  }
}
