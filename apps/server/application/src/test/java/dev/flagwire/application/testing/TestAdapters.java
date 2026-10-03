package dev.flagwire.application.testing;

import dev.flagwire.application.port.out.ApiKeyStore;
import dev.flagwire.application.port.out.AuditLog;
import dev.flagwire.application.port.out.ChangeFeed;
import dev.flagwire.application.port.out.ChangeLog;
import dev.flagwire.application.port.out.FlagRepository;
import dev.flagwire.application.port.out.ProjectRepository;
import dev.flagwire.application.port.out.PropagationStats;
import dev.flagwire.application.port.out.SegmentRepository;
import dev.flagwire.application.port.out.TransactionRunner;

public interface TestAdapters {

  FlagRepository flags();

  SegmentRepository segments();

  ProjectRepository projects();

  ApiKeyStore apiKeys();

  AuditLog auditLog();

  ChangeLog changeLog();

  ChangeFeed changeFeed();

  PropagationStats propagationStats();

  TransactionRunner transactions();
}
