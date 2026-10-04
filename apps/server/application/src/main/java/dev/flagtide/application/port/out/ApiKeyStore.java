package dev.flagtide.application.port.out;

import dev.flagtide.domain.access.ApiKey;
import dev.flagtide.domain.value.EnvironmentRef;
import java.util.List;
import java.util.Optional;

public interface ApiKeyStore {

  void save(ApiKey key);

  Optional<ApiKey> findByLookup(String lookup);

  List<ApiKey> findByEnvironment(EnvironmentRef environment);
}
