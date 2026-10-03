package dev.flagwire.adapter.out.postgres;

import dev.flagwire.application.port.out.ApiKeyStore;
import dev.flagwire.application.port.out.AuditLog;
import dev.flagwire.application.port.out.ChangeFeed;
import dev.flagwire.application.port.out.ChangeLog;
import dev.flagwire.application.port.out.FlagRepository;
import dev.flagwire.application.port.out.ProjectRepository;
import dev.flagwire.application.port.out.PropagationStats;
import dev.flagwire.application.port.out.SegmentRepository;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.port.out.TransactionRunner;
import dev.flagwire.application.support.SystemTimeSource;
import dev.flagwire.application.testing.TestAdapters;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jdbi.v3.core.Jdbi;

final class PostgresTestAdapters implements TestAdapters, AutoCloseable {

  private static final String EMPTY_ALL_TABLES =
      """
      TRUNCATE projects, environment_versions, flags, segments, api_keys, change_log, audit_log,
        instance_stats
      RESTART IDENTITY CASCADE
      """;

  private static final Vertx VERTX = Vertx.vertx();

  private final PostgresAdapters delegate;
  private final PostgresPropagationStats stats;
  private PostgresChangeFeed feed;

  private PostgresTestAdapters(PostgresAdapters delegate, PostgresPropagationStats stats) {
    this.delegate = delegate;
    this.stats = stats;
  }

  static PostgresTestAdapters onEmptyDatabase(DataSource dataSource, int changeLogRetention) {
    return onEmptyDatabase(dataSource, changeLogRetention, new SystemTimeSource(), "test");
  }

  static PostgresTestAdapters onEmptyDatabase(
      DataSource dataSource, int changeLogRetention, TimeSource timeSource) {
    return onEmptyDatabase(dataSource, changeLogRetention, timeSource, "test");
  }

  static PostgresTestAdapters onEmptyDatabase(
      DataSource dataSource, int changeLogRetention, TimeSource timeSource, String instanceId) {
    Jdbi.create(dataSource).useHandle(handle -> handle.execute(EMPTY_ALL_TABLES));
    PostgresAdapters adapters = PostgresAdapters.create(dataSource, changeLogRetention);
    return new PostgresTestAdapters(
        adapters, new PostgresPropagationStats(adapters.transactions(), timeSource, instanceId));
  }

  static PgConnectOptions listenerOptions(String applicationName) {
    Config config = ConfigProvider.getConfig();
    return ListenerConnection.optionsFromJdbcUrl(
        config.getValue("quarkus.datasource.jdbc.url", String.class),
        config.getValue("quarkus.datasource.username", String.class),
        config.getValue("quarkus.datasource.password", String.class),
        applicationName);
  }

  PostgresPropagationStats stats() {
    return this.stats;
  }

  @Override
  public void close() {
    if (this.feed != null) {
      this.feed.close();
    }
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
  public synchronized ChangeFeed changeFeed() {
    if (this.feed == null) {
      this.feed = new PostgresChangeFeed(VERTX, listenerOptions("flagwire-test-listener"));
    }
    return this.feed;
  }

  @Override
  public PropagationStats propagationStats() {
    return this.stats;
  }

  @Override
  public TransactionRunner transactions() {
    return this.delegate.transactions();
  }
}
