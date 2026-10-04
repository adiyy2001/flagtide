package dev.flagtide.application.usecase;

import dev.flagtide.application.port.out.ApiKeyStore;
import dev.flagtide.application.security.ApiKeys;
import dev.flagtide.application.security.Principal;
import dev.flagtide.domain.access.ApiKey;
import dev.flagtide.domain.access.ApiKeyKind;
import dev.flagtide.domain.error.FlagtideException;

public final class Authenticate {

  private final ApiKeyStore apiKeys;

  public Authenticate(ApiKeyStore apiKeys) {
    this.apiKeys = apiKeys;
  }

  public Principal execute(String secret) {
    ApiKeyKind kind =
        ApiKeys.kindOf(secret).orElseThrow(() -> FlagtideException.unauthorized("unknown key"));
    ApiKey key =
        this.apiKeys
            .findByLookup(ApiKeys.lookupFor(kind, secret))
            .filter(candidate -> candidate.kind() == kind)
            .orElseThrow(() -> FlagtideException.unauthorized("unknown key"));
    return Principal.of(key);
  }
}
