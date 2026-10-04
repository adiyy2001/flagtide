package dev.flagtide.application.testing;

import dev.flagtide.application.port.out.ApiKeyStore;
import dev.flagtide.application.port.out.AuditLog;
import dev.flagtide.application.port.out.ChangeFeed;
import dev.flagtide.application.port.out.ChangeLog;
import dev.flagtide.application.port.out.FlagRepository;
import dev.flagtide.application.port.out.ProjectRepository;
import dev.flagtide.application.port.out.PropagationStats;
import dev.flagtide.application.port.out.SegmentRepository;
import dev.flagtide.application.port.out.TransactionRunner;

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
