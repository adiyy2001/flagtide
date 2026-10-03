package dev.flagwire.adapter.postgres;

import dev.flagwire.application.port.out.ApiKeyStore;
import dev.flagwire.domain.access.ApiKey;
import dev.flagwire.domain.access.ApiKeyKind;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.ProjectKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class PostgresApiKeyStore implements ApiKeyStore {

  private static final String INSERT =
      """
      INSERT INTO api_keys (id, kind, project_key, environment_key, label, lookup, created_at)
      VALUES (:id, :kind, :project, :environment, :label, :lookup, :createdAt)
      ON CONFLICT DO NOTHING
      """;

  private static final String COLUMNS =
      "SELECT id, kind, project_key, environment_key, label, lookup, created_at FROM api_keys";

  private static final String BY_LOOKUP = COLUMNS + " WHERE lookup = :lookup";

  private static final String BY_ENVIRONMENT =
      COLUMNS
          + " WHERE project_key = :project AND environment_key = :environment"
          + " ORDER BY created_at, id";

  private final PostgresDatabase database;

  public PostgresApiKeyStore(PostgresDatabase database) {
    this.database = database;
  }

  @Override
  public void save(ApiKey key) {
    int inserted =
        this.database.query(
            handle ->
                handle
                    .createUpdate(INSERT)
                    .bind("id", key.id())
                    .bind("kind", key.kind().name())
                    .bind("project", key.project().value())
                    .bind("environment", key.environment().value())
                    .bind("label", key.label())
                    .bind("lookup", key.lookup())
                    .bind("createdAt", Documents.toDatabase(key.createdAt()))
                    .execute());
    if (inserted == 0) {
      throw FlagwireException.conflict("an api key with this secret already exists");
    }
  }

  @Override
  public Optional<ApiKey> findByLookup(String lookup) {
    return this.database.query(
        handle ->
            handle
                .createQuery(BY_LOOKUP)
                .bind("lookup", lookup)
                .map((rows, context) -> key(rows))
                .findOne());
  }

  @Override
  public List<ApiKey> findByEnvironment(EnvironmentRef environment) {
    return this.database.query(
        handle ->
            handle
                .createQuery(BY_ENVIRONMENT)
                .bind("project", environment.project().value())
                .bind("environment", environment.environment().value())
                .map((rows, context) -> key(rows))
                .list());
  }

  private static ApiKey key(ResultSet rows) throws SQLException {
    return new ApiKey(
        rows.getString("id"),
        ApiKeyKind.valueOf(rows.getString("kind")),
        new ProjectKey(rows.getString("project_key")),
        new EnvironmentKey(rows.getString("environment_key")),
        rows.getString("label"),
        rows.getString("lookup"),
        Documents.time(rows, "created_at"));
  }
}
