package dev.flagtide.adapter.out.memory.usecase;

import dev.flagtide.adapter.out.memory.MemoryAdapters;
import dev.flagtide.application.security.Authorizer;
import dev.flagtide.application.security.Principal;
import dev.flagtide.application.testing.MutableTimeSource;
import dev.flagtide.application.testing.Samples;
import dev.flagtide.application.testing.SequentialIds;
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
import dev.flagtide.application.usecase.IssuedKey;
import dev.flagtide.application.usecase.ListApiKeys;
import dev.flagtide.application.usecase.ListFlags;
import dev.flagtide.application.usecase.ListSegments;
import dev.flagtide.application.usecase.ProvisionedProject;
import dev.flagtide.application.usecase.ReadAuditLog;
import dev.flagtide.application.usecase.ReleaseKillSwitch;
import dev.flagtide.application.usecase.SaveSegment;
import dev.flagtide.application.usecase.SyncSince;
import dev.flagtide.application.usecase.ToggleFlag;
import dev.flagtide.application.usecase.UpdateFlagDefinition;
import dev.flagtide.domain.access.ApiKeyKind;
import dev.flagtide.domain.error.FlagtideError;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.ProjectKey;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

final class Flagtide {

  static final ProjectKey PROJECT = new ProjectKey("shop");
  static final EnvironmentKey DEV = new EnvironmentKey("dev");
  static final EnvironmentKey STAGING = new EnvironmentKey("staging");
  static final EnvironmentKey PROD = new EnvironmentKey("prod");

  final MutableTimeSource time = new MutableTimeSource(Samples.NOW);
  final MemoryAdapters adapters;
  final Authorizer authorizer = new Authorizer();

  final CreateProject createProject;
  final CreateEnvironment createEnvironment;
  final CreateFlag createFlag;
  final UpdateFlagDefinition updateFlagDefinition;
  final ConfigureFlag configureFlag;
  final ToggleFlag toggleFlag;
  final EngageKillSwitch engageKillSwitch;
  final ReleaseKillSwitch releaseKillSwitch;
  final ArchiveFlag archiveFlag;
  final SaveSegment saveSegment;
  final DeleteSegment deleteSegment;
  final ListFlags listFlags;
  final GetFlag getFlag;
  final ListSegments listSegments;
  final GetProject getProject;
  final ListApiKeys listApiKeys;
  final ReadAuditLog readAuditLog;
  final Authenticate authenticate;
  final BuildSnapshot buildSnapshot;
  final SyncSince syncSince;

  final ProvisionedProject provisioned;

  Flagtide() {
    this(MemoryAdapters.DEFAULT_RETENTION);
  }

  Flagtide(int changeLogRetention) {
    this.adapters = MemoryAdapters.create(this.time, changeLogRetention);
    SequentialIds ids = new SequentialIds();
    var transactions = this.adapters.transactions();
    this.createProject =
        new CreateProject(
            transactions,
            this.adapters.projects(),
            this.adapters.apiKeys(),
            this.adapters.changeLog(),
            this.adapters.auditLog(),
            this.time,
            ids);
    this.createEnvironment =
        new CreateEnvironment(
            transactions,
            this.adapters.projects(),
            this.adapters.flags(),
            this.adapters.apiKeys(),
            this.adapters.changeLog(),
            this.adapters.auditLog(),
            this.time,
            ids,
            this.authorizer);
    this.createFlag =
        new CreateFlag(
            transactions,
            this.adapters.projects(),
            this.adapters.flags(),
            this.adapters.changeLog(),
            this.adapters.auditLog(),
            this.time,
            ids,
            this.authorizer);
    this.updateFlagDefinition =
        new UpdateFlagDefinition(
            transactions,
            this.adapters.flags(),
            this.adapters.changeLog(),
            this.adapters.auditLog(),
            this.time,
            ids,
            this.authorizer);
    this.configureFlag =
        new ConfigureFlag(
            transactions,
            this.adapters.flags(),
            this.adapters.segments(),
            this.adapters.changeLog(),
            this.adapters.auditLog(),
            this.time,
            ids,
            this.authorizer);
    this.toggleFlag =
        new ToggleFlag(
            transactions,
            this.adapters.flags(),
            this.adapters.changeLog(),
            this.adapters.auditLog(),
            this.time,
            ids,
            this.authorizer);
    this.engageKillSwitch =
        new EngageKillSwitch(
            transactions,
            this.adapters.flags(),
            this.adapters.changeLog(),
            this.adapters.auditLog(),
            this.time,
            ids,
            this.authorizer);
    this.releaseKillSwitch =
        new ReleaseKillSwitch(
            transactions,
            this.adapters.flags(),
            this.adapters.changeLog(),
            this.adapters.auditLog(),
            this.time,
            ids,
            this.authorizer);
    this.archiveFlag =
        new ArchiveFlag(
            transactions,
            this.adapters.flags(),
            this.adapters.changeLog(),
            this.adapters.auditLog(),
            this.time,
            ids,
            this.authorizer);
    this.saveSegment =
        new SaveSegment(
            transactions,
            this.adapters.segments(),
            this.adapters.changeLog(),
            this.adapters.auditLog(),
            this.time,
            ids,
            this.authorizer);
    this.deleteSegment =
        new DeleteSegment(
            transactions,
            this.adapters.segments(),
            this.adapters.flags(),
            this.adapters.changeLog(),
            this.adapters.auditLog(),
            this.time,
            ids,
            this.authorizer);
    this.listFlags = new ListFlags(this.adapters.flags(), this.authorizer);
    this.getFlag = new GetFlag(this.adapters.flags(), this.authorizer);
    this.listSegments = new ListSegments(this.adapters.segments(), this.authorizer);
    this.getProject = new GetProject(this.adapters.projects(), this.authorizer);
    this.listApiKeys = new ListApiKeys(this.adapters.apiKeys(), this.authorizer);
    this.readAuditLog = new ReadAuditLog(this.adapters.auditLog(), this.authorizer);
    this.authenticate = new Authenticate(this.adapters.apiKeys());
    this.buildSnapshot =
        new BuildSnapshot(
            transactions,
            this.adapters.flags(),
            this.adapters.segments(),
            this.adapters.changeLog());
    this.syncSince = new SyncSince(transactions, this.adapters.changeLog(), this.buildSnapshot);
    this.provisioned =
        this.createProject.execute(new CreateProject.Command(PROJECT, "Shop", "founder")).value();
  }

  Principal admin(EnvironmentKey environment) {
    return this.principal(environment, ApiKeyKind.ADMIN);
  }

  Principal sdk(EnvironmentKey environment) {
    return this.principal(environment, ApiKeyKind.SDK);
  }

  String secret(EnvironmentKey environment, ApiKeyKind kind) {
    return this.issued(environment, kind).secret();
  }

  Principal principal(EnvironmentKey environment, ApiKeyKind kind) {
    return this.authenticate.execute(this.secret(environment, kind));
  }

  FlagtideError failure(ThrowingCallable work) {
    try {
      work.call();
    } catch (FlagtideException failure) {
      return failure.error();
    } catch (Throwable other) {
      throw new AssertionError("expected a FlagtideException but got " + other, other);
    }
    throw new AssertionError("expected a FlagtideException but nothing was thrown");
  }

  private IssuedKey issued(EnvironmentKey environment, ApiKeyKind kind) {
    return this.provisioned.keys().stream()
        .filter(key -> key.key().environment().equals(environment) && key.key().kind() == kind)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no " + kind + " key for " + environment));
  }
}
