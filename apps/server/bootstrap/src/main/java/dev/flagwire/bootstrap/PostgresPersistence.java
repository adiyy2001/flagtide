package dev.flagwire.bootstrap;

import dev.flagwire.adapter.out.postgres.PostgresAdapters;
import dev.flagwire.application.port.out.ApiKeyStore;
import dev.flagwire.application.port.out.AuditLog;
import dev.flagwire.application.port.out.ChangeLog;
import dev.flagwire.application.port.out.FlagRepository;
import dev.flagwire.application.port.out.ProjectRepository;
import dev.flagwire.application.port.out.SegmentRepository;
import dev.flagwire.application.port.out.TransactionRunner;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@IfBuildProperty(name = "flagwire.persistence", stringValue = "postgres")
public class PostgresPersistence {

  @Produces
  @Singleton
  PostgresAdapters adapters(
      DataSource dataSource,
      @ConfigProperty(name = "flagwire.change-log.retention") int retention) {
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
}
