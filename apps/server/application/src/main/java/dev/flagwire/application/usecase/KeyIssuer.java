package dev.flagwire.application.usecase;

import dev.flagwire.application.port.out.ApiKeyStore;
import dev.flagwire.application.port.out.IdGenerator;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.security.ApiKeys;
import dev.flagwire.domain.access.ApiKey;
import dev.flagwire.domain.access.ApiKeyKind;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.ProjectKey;
import java.util.List;

final class KeyIssuer {

  private final ApiKeyStore apiKeys;
  private final IdGenerator ids;
  private final TimeSource timeSource;

  KeyIssuer(ApiKeyStore apiKeys, IdGenerator ids, TimeSource timeSource) {
    this.apiKeys = apiKeys;
    this.ids = ids;
    this.timeSource = timeSource;
  }

  List<IssuedKey> issueFor(ProjectKey project, EnvironmentKey environment) {
    return List.of(
        this.issue(project, environment, ApiKeyKind.ADMIN),
        this.issue(project, environment, ApiKeyKind.SDK));
  }

  private IssuedKey issue(ProjectKey project, EnvironmentKey environment, ApiKeyKind kind) {
    String secret = ApiKeys.secret(kind, this.ids.newToken());
    ApiKey key =
        new ApiKey(
            this.ids.newId(),
            kind,
            project,
            environment,
            environment.value() + "-" + kind.name().toLowerCase(),
            ApiKeys.lookupFor(kind, secret),
            this.timeSource.now());
    this.apiKeys.save(key);
    return new IssuedKey(key, secret);
  }
}
