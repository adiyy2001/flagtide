package dev.flagwire.adapter.out.postgres;

import dev.flagwire.application.port.out.AuditLog;
import dev.flagwire.application.port.out.AuditQuery;
import dev.flagwire.domain.audit.AuditEntry;
import dev.flagwire.domain.audit.EntityType;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.EnvironmentVersion;
import dev.flagwire.domain.value.ProjectKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.statement.Query;

public final class PostgresAuditLog implements AuditLog {

  private static final String INSERT =
      """
      INSERT INTO audit_log (id, project_key, environment_key, environment_version, entity_type,
                             entity_key, action, author, occurred_at, before_state, after_state)
      VALUES (:id, :project, :environment, :environmentVersion, :entityType, :entityKey, :action,
              :author, :occurredAt, %s, %s)
      """
          .formatted(Documents.jsonb("before"), Documents.jsonb("after"));

  private static final String SELECT =
      """
      SELECT id, project_key, environment_key, environment_version, entity_type, entity_key, action,
             author, occurred_at, before_state, after_state
      FROM audit_log
      """;

  private final PostgresDatabase database;

  public PostgresAuditLog(PostgresDatabase database) {
    this.database = database;
  }

  @Override
  public void append(AuditEntry entry) {
    this.database.query(
        handle ->
            handle
                .createUpdate(INSERT)
                .bind("id", entry.id())
                .bind("project", entry.project().value())
                .bind("environment", entry.environment().map(EnvironmentKey::value).orElse(null))
                .bind(
                    "environmentVersion",
                    entry.environmentVersion().map(EnvironmentVersion::value).orElse(null))
                .bind("entityType", entry.entityType().name())
                .bind("entityKey", entry.entityKey())
                .bind("action", entry.action())
                .bind("author", entry.author())
                .bind("occurredAt", Documents.toDatabase(entry.at()))
                .bind("before", Documents.optionalText(entry.before()).orElse(null))
                .bind("after", Documents.optionalText(entry.after()).orElse(null))
                .execute());
  }

  @Override
  public List<AuditEntry> find(AuditQuery query) {
    List<String> conditions = new ArrayList<>(List.of("project_key = :project"));
    query.environment().ifPresent(environment -> conditions.add("environment_key = :environment"));
    query.entityKey().ifPresent(entity -> conditions.add("entity_key = :entityKey"));
    String sql =
        SELECT
            + " WHERE "
            + String.join(" AND ", conditions)
            + " ORDER BY sequence DESC LIMIT :limit OFFSET :offset";
    return this.database.query(
        handle -> {
          Query statement =
              handle
                  .createQuery(sql)
                  .bind("project", query.project().value())
                  .bind("limit", query.limit())
                  .bind("offset", query.offset());
          query
              .environment()
              .ifPresent(environment -> statement.bind("environment", environment.value()));
          query.entityKey().ifPresent(entity -> statement.bind("entityKey", entity));
          return statement.map((rows, context) -> entry(rows)).list();
        });
  }

  private static AuditEntry entry(ResultSet rows) throws SQLException {
    long version = rows.getLong("environment_version");
    boolean hasVersion = !rows.wasNull();
    return new AuditEntry(
        rows.getString("id"),
        new ProjectKey(rows.getString("project_key")),
        Optional.ofNullable(rows.getString("environment_key")).map(EnvironmentKey::new),
        hasVersion ? Optional.of(EnvironmentVersion.of(version)) : Optional.empty(),
        EntityType.valueOf(rows.getString("entity_type")),
        rows.getString("entity_key"),
        rows.getString("action"),
        rows.getString("author"),
        Documents.time(rows, "occurred_at"),
        Documents.optionalDocument(rows, "before_state"),
        Documents.optionalDocument(rows, "after_state"));
  }
}
