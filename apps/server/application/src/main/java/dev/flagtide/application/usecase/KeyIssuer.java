package dev.flagtide.application.usecase;

import dev.flagtide.application.port.out.ApiKeyStore;
import dev.flagtide.application.port.out.IdGenerator;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.security.ApiKeys;
import dev.flagtide.domain.access.ApiKey;
import dev.flagtide.domain.access.ApiKeyKind;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.ProjectKey;
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
