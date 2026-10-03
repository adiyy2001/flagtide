package dev.flagwire.adapter.out.postgres;

import javax.sql.DataSource;

public record PostgresAdapters(
    PostgresDatabase transactions,
    PostgresFlagRepository flags,
    PostgresSegmentRepository segments,
    PostgresProjectRepository projects,
    PostgresApiKeyStore apiKeys,
    PostgresAuditLog auditLog,
    PostgresChangeLog changeLog) {

  public static final int DEFAULT_RETENTION = 1000;

  public static PostgresAdapters create(DataSource dataSource) {
    return create(dataSource, DEFAULT_RETENTION);
  }

  public static PostgresAdapters create(DataSource dataSource, int changeLogRetention) {
    PostgresDatabase database = new PostgresDatabase(dataSource);
    return new PostgresAdapters(
        database,
        new PostgresFlagRepository(database),
        new PostgresSegmentRepository(database),
        new PostgresProjectRepository(database),
        new PostgresApiKeyStore(database),
        new PostgresAuditLog(database),
        new PostgresChangeLog(database, changeLogRetention));
  }
}
