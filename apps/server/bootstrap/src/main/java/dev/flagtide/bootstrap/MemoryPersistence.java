package dev.flagtide.bootstrap;

import dev.flagtide.adapter.out.memory.MemoryAdapters;
import dev.flagtide.application.port.out.ApiKeyStore;
import dev.flagtide.application.port.out.AuditLog;
import dev.flagtide.application.port.out.ChangeFeed;
import dev.flagtide.application.port.out.ChangeLog;
import dev.flagtide.application.port.out.FlagRepository;
import dev.flagtide.application.port.out.ProjectRepository;
import dev.flagtide.application.port.out.PropagationStats;
import dev.flagtide.application.port.out.SegmentRepository;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.port.out.TransactionRunner;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@IfBuildProperty(name = "flagtide.persistence", stringValue = "memory")
public class MemoryPersistence {

  @Produces
  @Singleton
  MemoryAdapters adapters(
      TimeSource timeSource,
      @ConfigProperty(name = "flagtide.change-log.retention") int retention) {
    return MemoryAdapters.create(timeSource, retention);
  }

  @Produces
  @Singleton
  TransactionRunner transactions(MemoryAdapters adapters) {
    return adapters.transactions();
  }

  @Produces
  @Singleton
  FlagRepository flags(MemoryAdapters adapters) {
    return adapters.flags();
  }

  @Produces
  @Singleton
  SegmentRepository segments(MemoryAdapters adapters) {
    return adapters.segments();
  }

  @Produces
  @Singleton
  ProjectRepository projects(MemoryAdapters adapters) {
    return adapters.projects();
  }

  @Produces
  @Singleton
  ApiKeyStore apiKeys(MemoryAdapters adapters) {
    return adapters.apiKeys();
  }

  @Produces
  @Singleton
  AuditLog auditLog(MemoryAdapters adapters) {
    return adapters.auditLog();
  }

  @Produces
  @Singleton
  ChangeLog changeLog(MemoryAdapters adapters) {
    return adapters.changeLog();
  }

  @Produces
  @Singleton
  ChangeFeed changeFeed(MemoryAdapters adapters) {
    return adapters.changeFeed();
  }

  @Produces
  @Singleton
  PropagationStats propagationStats(MemoryAdapters adapters) {
    return adapters.propagationStats();
  }
}
