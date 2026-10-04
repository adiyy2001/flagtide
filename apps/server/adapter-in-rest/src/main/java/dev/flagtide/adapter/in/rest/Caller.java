package dev.flagtide.adapter.in.rest;

import dev.flagtide.application.security.Principal;
import dev.flagtide.domain.error.FlagtideException;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class Caller {

  private Principal principal;

  void authenticated(Principal authenticated) {
    this.principal = authenticated;
  }

  public Principal principal() {
    if (this.principal == null) {
      throw FlagtideException.unauthorized("a bearer key is required");
    }
    return this.principal;
  }

  public Principal inProject(String project) {
    Principal current = this.principal();
    if (!current.project().value().equals(project)) {
      throw FlagtideException.forbidden("the key belongs to another project");
    }
    return current;
  }
}
