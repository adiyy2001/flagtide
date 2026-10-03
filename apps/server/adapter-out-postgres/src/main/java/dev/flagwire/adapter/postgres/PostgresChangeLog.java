package dev.flagwire.adapter.postgres;

import dev.flagwire.application.change.Change;
import dev.flagwire.application.change.ChangeJson;
import dev.flagwire.application.change.ChangeLogEntry;
import dev.flagwire.application.port.out.ChangeLog;
import dev.flagwire.application.port.out.ChangeNotification;
import dev.flagwire.domain.json.JsonText;
import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.EnvironmentVersion;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Handle;

public final class PostgresChangeLog implements ChangeLog {

  private static final String BUMP_VERSION =
      """
      INSERT INTO environment_versions (project_key, environment_key, version)
      VALUES (:project, :environment, 1)
      ON CONFLICT (project_key, environment_key)
      DO UPDATE SET version = environment_versions.version + 1
      RETURNING version
      """;

  private static final String INSERT_ENTRY =
      """
      INSERT INTO change_log (project_key, environment_key, version, committed_at, changes)
      VALUES (:project, :environment, :version, :committedAt, %s)
      """
          .formatted(Documents.jsonb("changes"));

  private static final String PRUNE =
      """
      DELETE FROM change_log
      WHERE project_key = :project AND environment_key = :environment AND version <= :cutoff
      """;

  private static final String NOTIFY = "SELECT pg_notify(:channel, :payload)";

  private static final String CURRENT_VERSION =
      """
      SELECT version FROM environment_versions
      WHERE project_key = :project AND environment_key = :environment
      """;

  private static final String ENTRIES_AFTER =
      """
      SELECT version, committed_at, changes FROM change_log
      WHERE project_key = :project AND environment_key = :environment AND version > :version
      ORDER BY version
      """;

  private static final String OLDEST =
      """
      SELECT min(version) AS oldest FROM change_log
      WHERE project_key = :project AND environment_key = :environment
      """;

  private final PostgresDatabase database;
  private final int retention;

  public PostgresChangeLog(PostgresDatabase database, int retention) {
    if (retention < 1) {
      throw new IllegalArgumentException("the change log keeps at least one entry");
    }
    this.database = database;
    this.retention = retention;
  }

  @Override
  public EnvironmentVersion append(
      EnvironmentRef environment, ZonedDateTime committedAt, List<Change> changes) {
    return this.database.atomically(
        handle -> {
          long version = this.bumpVersion(handle, environment);
          this.insertEntry(handle, environment, version, committedAt, changes);
          this.prune(handle, environment, version);
          EnvironmentVersion assigned = EnvironmentVersion.of(version);
          this.notifyListeners(handle, new ChangeNotification(environment, assigned));
          return assigned;
        });
  }

  @Override
  public EnvironmentVersion currentVersion(EnvironmentRef environment) {
    return this.database.query(
        handle ->
            handle
                .createQuery(CURRENT_VERSION)
                .bind("project", environment.project().value())
                .bind("environment", environment.environment().value())
                .mapTo(Long.class)
                .findOne()
                .map(EnvironmentVersion::of)
                .orElse(EnvironmentVersion.ZERO));
  }

  @Override
  public List<ChangeLogEntry> entriesAfter(EnvironmentRef environment, EnvironmentVersion version) {
    return this.database.query(
        handle ->
            handle
                .createQuery(ENTRIES_AFTER)
                .bind("project", environment.project().value())
                .bind("environment", environment.environment().value())
                .bind("version", version.value())
                .map(
                    (rows, context) ->
                        new ChangeLogEntry(
                            EnvironmentVersion.of(rows.getLong("version")),
                            Documents.time(rows, "committed_at"),
                            ChangeJson.listFromJson(Documents.document(rows, "changes"))))
                .list());
  }

  @Override
  public Optional<EnvironmentVersion> oldestRetained(EnvironmentRef environment) {
    return this.database.query(
        handle ->
            handle
                .createQuery(OLDEST)
                .bind("project", environment.project().value())
                .bind("environment", environment.environment().value())
                .map(
                    (rows, context) -> {
                      long oldest = rows.getLong("oldest");
                      return rows.wasNull()
                          ? Optional.<EnvironmentVersion>empty()
                          : Optional.of(EnvironmentVersion.of(oldest));
                    })
                .one());
  }

  private long bumpVersion(Handle handle, EnvironmentRef environment) {
    return handle
        .createQuery(BUMP_VERSION)
        .bind("project", environment.project().value())
        .bind("environment", environment.environment().value())
        .mapTo(Long.class)
        .one();
  }

  private void insertEntry(
      Handle handle,
      EnvironmentRef environment,
      long version,
      ZonedDateTime committedAt,
      List<Change> changes) {
    handle
        .createUpdate(INSERT_ENTRY)
        .bind("project", environment.project().value())
        .bind("environment", environment.environment().value())
        .bind("version", version)
        .bind("committedAt", Documents.toDatabase(committedAt))
        .bind("changes", JsonText.write(ChangeJson.toJson(changes)))
        .execute();
  }

  private void prune(Handle handle, EnvironmentRef environment, long version) {
    handle
        .createUpdate(PRUNE)
        .bind("project", environment.project().value())
        .bind("environment", environment.environment().value())
        .bind("cutoff", version - this.retention)
        .execute();
  }

  private void notifyListeners(Handle handle, ChangeNotification notification) {
    handle
        .createQuery(NOTIFY)
        .bind("channel", ChangeNotifications.CHANNEL)
        .bind("payload", ChangeNotifications.payload(notification))
        .map((rows, context) -> Boolean.TRUE)
        .one();
  }
}
