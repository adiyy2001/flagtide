package dev.flagtide.bootstrap;

import dev.flagtide.adapter.out.postgres.ListenerConnection;
import dev.flagtide.adapter.out.postgres.PostgresAdapters;
import dev.flagtide.adapter.out.postgres.PostgresChangeFeed;
import dev.flagtide.adapter.out.postgres.PostgresPropagationStats;
import dev.flagtide.application.port.out.ApiKeyStore;
import dev.flagtide.application.port.out.AuditLog;
import dev.flagtide.application.port.out.ChangeLog;
import dev.flagtide.application.port.out.FlagRepository;
import dev.flagtide.application.port.out.ProjectRepository;
import dev.flagtide.application.port.out.PropagationStats;
import dev.flagtide.application.port.out.SegmentRepository;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.port.out.TransactionRunner;
import io.quarkus.arc.properties.IfBuildProperty;
import io.vertx.core.Vertx;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@IfBuildProperty(name = "flagtide.persistence", stringValue = "postgres")
public class PostgresPersistence {

  @Produces
  @Singleton
  PostgresAdapters adapters(
      DataSource dataSource,
      @ConfigProperty(name = "flagtide.change-log.retention") int retention) {
    return PostgresAdapters.create(dataSource, retention);
  }

  @Produces
  @Singleton
  TransactionRunner transactions(PostgresAdapters adapters) {
    return adapters.transactions();
  }

  @Produces
  @Singleton
  FlagRepository flags(PostgresAdapters adapters) {
    return adapters.flags();
  }

  @Produces
  @Singleton
  SegmentRepository segments(PostgresAdapters adapters) {
    return adapters.segments();
  }

  @Produces
  @Singleton
  ProjectRepository projects(PostgresAdapters adapters) {
    return adapters.projects();
  }

  @Produces
  @Singleton
  ApiKeyStore apiKeys(PostgresAdapters adapters) {
    return adapters.apiKeys();
  }

  @Produces
  @Singleton
  AuditLog auditLog(PostgresAdapters adapters) {
    return adapters.auditLog();
  }

  @Produces
  @Singleton
  ChangeLog changeLog(PostgresAdapters adapters) {
    return adapters.changeLog();
  }

  @Produces
  @Singleton
  PropagationStats propagationStats(
      PostgresAdapters adapters, TimeSource timeSource, InstanceId instanceId) {
    return new PostgresPropagationStats(adapters.transactions(), timeSource, instanceId.value());
  }

  @Produces
  @Singleton
  PostgresChangeFeed changeFeed(
      Vertx vertx,
      InstanceId instanceId,
      @ConfigProperty(name = "quarkus.datasource.jdbc.url") String jdbcUrl,
      @ConfigProperty(name = "quarkus.datasource.username") String username,
      @ConfigProperty(name = "quarkus.datasource.password") String password) {
    return new PostgresChangeFeed(
        vertx,
        ListenerConnection.optionsFromJdbcUrl(
            jdbcUrl, username, password, "flagtide-listener-" + instanceId.value()));
  }

  void closeFeed(@Disposes PostgresChangeFeed feed) {
    feed.close();
  }
}
