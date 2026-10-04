package dev.flagtide.adapter.out.memory;

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
import dev.flagtide.application.testing.TestAdapters;

final class MemoryTestAdapters implements TestAdapters {

  private final MemoryAdapters delegate;

  MemoryTestAdapters(TimeSource timeSource, int changeLogRetention) {
    this.delegate = MemoryAdapters.create(timeSource, changeLogRetention);
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
    return this.delegate.changeFeed();
  }

  @Override
  public PropagationStats propagationStats() {
    return this.delegate.propagationStats();
  }

  @Override
  public TransactionRunner transactions() {
    return this.delegate.transactions();
  }
}
