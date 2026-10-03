package dev.flagwire.application.usecase;

import dev.flagwire.application.port.out.ApiKeyStore;
import dev.flagwire.application.security.Authorizer;
import dev.flagwire.application.security.Principal;
import dev.flagwire.domain.access.ApiKeyKind;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.EnvironmentRef;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ListApiKeys {

  public record KeyView(
      String id,
      ApiKeyKind kind,
      EnvironmentKey environment,
      String label,
      Optional<String> sdkKey) {}

  private final ApiKeyStore apiKeys;
  private final Authorizer authorizer;

  public ListApiKeys(ApiKeyStore apiKeys, Authorizer authorizer) {
    this.apiKeys = apiKeys;
    this.authorizer = authorizer;
  }

  public List<KeyView> execute(Principal principal, EnvironmentKey environment) {
    this.authorizer.requireAdmin(principal);
    return this.apiKeys
        .findByEnvironment(new EnvironmentRef(principal.project(), environment))
        .stream()
        .map(
            key ->
                new KeyView(
                    key.id(),
                    key.kind(),
                    key.environment(),
                    key.label(),
                    key.kind() == ApiKeyKind.SDK ? Optional.of(key.lookup()) : Optional.empty()))
        .sorted(Comparator.comparing(KeyView::kind).thenComparing(KeyView::id))
        .toList();
  }
}
