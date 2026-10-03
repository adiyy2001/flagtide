package dev.flagwire.application.usecase;

import dev.flagwire.application.port.out.ApiKeyStore;
import dev.flagwire.application.security.ApiKeys;
import dev.flagwire.application.security.Principal;
import dev.flagwire.domain.access.ApiKey;
import dev.flagwire.domain.access.ApiKeyKind;
import dev.flagwire.domain.error.FlagwireException;

public final class Authenticate {

  private final ApiKeyStore apiKeys;

  public Authenticate(ApiKeyStore apiKeys) {
    this.apiKeys = apiKeys;
  }

  public Principal execute(String secret) {
    ApiKeyKind kind =
        ApiKeys.kindOf(secret).orElseThrow(() -> FlagwireException.unauthorized("unknown key"));
    ApiKey key =
        this.apiKeys
            .findByLookup(ApiKeys.lookupFor(kind, secret))
            .filter(candidate -> candidate.kind() == kind)
            .orElseThrow(() -> FlagwireException.unauthorized("unknown key"));
    return Principal.of(key);
  }
}
