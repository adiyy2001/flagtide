package dev.flagwire.application.usecase;

import dev.flagwire.application.port.out.FlagRepository;
import dev.flagwire.application.security.Authorizer;
import dev.flagwire.application.security.Principal;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.flag.Flag;
import dev.flagwire.domain.value.FlagKey;

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
        .orElseThrow(() -> FlagwireException.notFound("flag", key.value()));
  }
}
