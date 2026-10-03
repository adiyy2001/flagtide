package dev.flagwire.adapter.out.memory;

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
import dev.flagwire.application.testing.TestAdapters;

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
