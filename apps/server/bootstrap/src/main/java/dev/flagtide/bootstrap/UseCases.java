package dev.flagtide.bootstrap;

import dev.flagtide.application.port.out.ApiKeyStore;
import dev.flagtide.application.port.out.AuditLog;
import dev.flagtide.application.port.out.ChangeLog;
import dev.flagtide.application.port.out.FlagRepository;
import dev.flagtide.application.port.out.IdGenerator;
import dev.flagtide.application.port.out.ProjectRepository;
import dev.flagtide.application.port.out.SegmentRepository;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.port.out.TransactionRunner;
import dev.flagtide.application.security.Authorizer;
import dev.flagtide.application.usecase.ArchiveFlag;
import dev.flagtide.application.usecase.Authenticate;
import dev.flagtide.application.usecase.BuildSnapshot;
import dev.flagtide.application.usecase.ConfigureFlag;
import dev.flagtide.application.usecase.CreateEnvironment;
import dev.flagtide.application.usecase.CreateFlag;
import dev.flagtide.application.usecase.CreateProject;
import dev.flagtide.application.usecase.DeleteSegment;
import dev.flagtide.application.usecase.EngageKillSwitch;
import dev.flagtide.application.usecase.GetFlag;
import dev.flagtide.application.usecase.GetProject;
import dev.flagtide.application.usecase.ListApiKeys;
import dev.flagtide.application.usecase.ListFlags;
import dev.flagtide.application.usecase.ListSegments;
import dev.flagtide.application.usecase.ReadAuditLog;
import dev.flagtide.application.usecase.ReleaseKillSwitch;
import dev.flagtide.application.usecase.SaveSegment;
import dev.flagtide.application.usecase.ToggleFlag;
import dev.flagtide.application.usecase.UpdateFlagDefinition;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

public class UseCases {

  @Produces
  @Singleton
  Authenticate authenticate(ApiKeyStore apiKeys) {
    return new Authenticate(apiKeys);
  }

  @Produces
  @Singleton
  CreateProject createProject(
      TransactionRunner transactions,
      ProjectRepository projects,
      ApiKeyStore apiKeys,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids) {
    return new CreateProject(transactions, projects, apiKeys, changeLog, auditLog, timeSource, ids);
  }

  @Produces
  @Singleton
  GetProject getProject(ProjectRepository projects, Authorizer authorizer) {
    return new GetProject(projects, authorizer);
  }

  @Produces
  @Singleton
  CreateEnvironment createEnvironment(
      TransactionRunner transactions,
      ProjectRepository projects,
      FlagRepository flags,
      ApiKeyStore apiKeys,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    return new CreateEnvironment(
        transactions, projects, flags, apiKeys, changeLog, auditLog, timeSource, ids, authorizer);
  }

  @Produces
  @Singleton
  ListApiKeys listApiKeys(ApiKeyStore apiKeys, Authorizer authorizer) {
    return new ListApiKeys(apiKeys, authorizer);
  }

  @Produces
  @Singleton
  ListFlags listFlags(FlagRepository flags, Authorizer authorizer) {
    return new ListFlags(flags, authorizer);
  }

  @Produces
  @Singleton
  GetFlag getFlag(FlagRepository flags, Authorizer authorizer) {
    return new GetFlag(flags, authorizer);
  }

  @Produces
  @Singleton
  CreateFlag createFlag(
      TransactionRunner transactions,
      ProjectRepository projects,
      FlagRepository flags,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    return new CreateFlag(
        transactions, projects, flags, changeLog, auditLog, timeSource, ids, authorizer);
  }

  @Produces
  @Singleton
  UpdateFlagDefinition updateFlagDefinition(
      TransactionRunner transactions,
      FlagRepository flags,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    return new UpdateFlagDefinition(
        transactions, flags, changeLog, auditLog, timeSource, ids, authorizer);
  }

  @Produces
  @Singleton
  ArchiveFlag archiveFlag(
      TransactionRunner transactions,
      FlagRepository flags,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    return new ArchiveFlag(transactions, flags, changeLog, auditLog, timeSource, ids, authorizer);
  }

  @Produces
  @Singleton
  ConfigureFlag configureFlag(
      TransactionRunner transactions,
      FlagRepository flags,
      SegmentRepository segments,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    return new ConfigureFlag(
        transactions, flags, segments, changeLog, auditLog, timeSource, ids, authorizer);
  }

  @Produces
  @Singleton
  ToggleFlag toggleFlag(
      TransactionRunner transactions,
      FlagRepository flags,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    return new ToggleFlag(transactions, flags, changeLog, auditLog, timeSource, ids, authorizer);
  }

  @Produces
  @Singleton
  EngageKillSwitch engageKillSwitch(
      TransactionRunner transactions,
      FlagRepository flags,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    return new EngageKillSwitch(
        transactions, flags, changeLog, auditLog, timeSource, ids, authorizer);
  }

  @Produces
  @Singleton
  ReleaseKillSwitch releaseKillSwitch(
      TransactionRunner transactions,
      FlagRepository flags,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    return new ReleaseKillSwitch(
        transactions, flags, changeLog, auditLog, timeSource, ids, authorizer);
  }

  @Produces
  @Singleton
  ListSegments listSegments(SegmentRepository segments, Authorizer authorizer) {
    return new ListSegments(segments, authorizer);
  }

  @Produces
  @Singleton
  SaveSegment saveSegment(
      TransactionRunner transactions,
      SegmentRepository segments,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    return new SaveSegment(
        transactions, segments, changeLog, auditLog, timeSource, ids, authorizer);
  }

  @Produces
  @Singleton
  DeleteSegment deleteSegment(
      TransactionRunner transactions,
      SegmentRepository segments,
      FlagRepository flags,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    return new DeleteSegment(
        transactions, segments, flags, changeLog, auditLog, timeSource, ids, authorizer);
  }

  @Produces
  @Singleton
  ReadAuditLog readAuditLog(AuditLog auditLog, Authorizer authorizer) {
    return new ReadAuditLog(auditLog, authorizer);
  }

  @Produces
  @Singleton
  BuildSnapshot buildSnapshot(
      TransactionRunner transactions,
      FlagRepository flags,
      SegmentRepository segments,
      ChangeLog changeLog) {
    return new BuildSnapshot(transactions, flags, segments, changeLog);
  }
}
