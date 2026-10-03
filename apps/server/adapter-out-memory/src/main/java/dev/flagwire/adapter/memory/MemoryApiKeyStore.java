package dev.flagwire.adapter.memory;

import dev.flagwire.application.port.out.ApiKeyStore;
import dev.flagwire.domain.access.ApiKey;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.value.EnvironmentRef;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MemoryApiKeyStore implements ApiKeyStore {

  private final MemoryDatabase database;
  private final Map<String, ApiKey> byLookup = new HashMap<>();

  public MemoryApiKeyStore(MemoryDatabase database) {
    this.database = database;
  }

  @Override
  public void save(ApiKey key) {
    this.database.write(
        () -> {
          if (this.byLookup.containsKey(key.lookup())) {
            throw FlagwireException.conflict("an api key with this secret already exists");
          }
          this.byLookup.put(key.lookup(), key);
          this.database.onRollback(() -> this.byLookup.remove(key.lookup()));
          return null;
        });
  }

  @Override
  public Optional<ApiKey> findByLookup(String lookup) {
    return this.database.read(() -> Optional.ofNullable(this.byLookup.get(lookup)));
  }

  @Override
  public List<ApiKey> findByEnvironment(EnvironmentRef environment) {
    return this.database.read(
        () ->
            this.byLookup.values().stream()
                .filter(key -> key.project().equals(environment.project()))
                .filter(key -> key.environment().equals(environment.environment()))
                .sorted(Comparator.comparing(ApiKey::createdAt).thenComparing(ApiKey::id))
                .toList());
  }
}
