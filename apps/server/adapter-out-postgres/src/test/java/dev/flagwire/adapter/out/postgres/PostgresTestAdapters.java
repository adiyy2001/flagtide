package dev.flagwire.adapter.out.postgres;

import dev.flagwire.application.port.out.ApiKeyStore;
import dev.flagwire.application.port.out.AuditLog;
import dev.flagwire.application.port.out.ChangeFeed;
import dev.flagwire.application.port.out.ChangeLog;
import dev.flagwire.application.port.out.FlagRepository;
import dev.flagwire.application.port.out.ProjectRepository;
import dev.flagwire.application.port.out.PropagationStats;
import dev.flagwire.application.port.out.SegmentRepository;
import dev.flagwire.application.port.out.TransactionRunner;
import dev.flagwire.application.testing.TestAdapters;
import javax.sql.DataSource;
import org.jdbi.v3.core.Jdbi;

final class PostgresTestAdapters implements TestAdapters {

  private static final String EMPTY_ALL_TABLES =
      """
      TRUNCATE projects, environment_versions, flags, segments, api_keys, change_log, audit_log
      RESTART IDENTITY CASCADE
      """;

  private final PostgresAdapters delegate;

  private PostgresTestAdapters(PostgresAdapters delegate) {
    this.delegate = delegate;
  }

  static PostgresTestAdapters onEmptyDatabase(DataSource dataSource, int changeLogRetention) {
    Jdbi.create(dataSource).useHandle(handle -> handle.execute(EMPTY_ALL_TABLES));
    return new PostgresTestAdapters(PostgresAdapters.create(dataSource, changeLogRetention));
  }

  PostgresAdapters adapters() {
    return this.delegate;
  }

  @Override
  public FlagRepository flags() {
    return this.delegate.flags();
  }

  @Override
  public SegmentRepository segments() {
    return this.delegate.segments();
  }

  @Override
  public ProjectRepository projects() {
    return this.delegate.projects();
  }

  @Override
  public ApiKeyStore apiKeys() {
    return this.delegate.apiKeys();
  }

  @Override
  public AuditLog auditLog() {
    return this.delegate.auditLog();
  }

  @Override
  public ChangeLog changeLog() {
    return this.delegate.changeLog();
  }

  @Override
  public ChangeFeed changeFeed() {
    throw new UnsupportedOperationException("the change feed arrives with the real-time milestone");
  }

  @Override
  public PropagationStats propagationStats() {
    throw new UnsupportedOperationException(
        "propagation statistics arrive with the real-time milestone");
  }

  @Override
  public TransactionRunner transactions() {
    return this.delegate.transactions();
  }
}
