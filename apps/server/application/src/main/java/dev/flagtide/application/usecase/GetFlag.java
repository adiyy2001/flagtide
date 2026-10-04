package dev.flagtide.application.usecase;

import dev.flagtide.application.port.out.FlagRepository;
import dev.flagtide.application.security.Authorizer;
import dev.flagtide.application.security.Principal;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.flag.Flag;
import dev.flagtide.domain.value.FlagKey;

public final class GetFlag {

  private final FlagRepository flags;
  private final Authorizer authorizer;

  public GetFlag(FlagRepository flags, Authorizer authorizer) {
    this.flags = flags;
    this.authorizer = authorizer;
  }

  public Flag execute(Principal principal, FlagKey key) {
    this.authorizer.requireAdmin(principal);
    return this.flags
        .find(principal.project(), key)
        .orElseThrow(() -> FlagtideException.notFound("flag", key.value()));
  }
}
