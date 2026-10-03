package dev.flagwire.application.port.out;

import dev.flagwire.domain.access.ApiKey;
import dev.flagwire.domain.value.EnvironmentRef;
import java.util.List;
import java.util.Optional;

public interface ApiKeyStore {

  void save(ApiKey key);

  Optional<ApiKey> findByLookup(String lookup);

  List<ApiKey> findByEnvironment(EnvironmentRef environment);
}
