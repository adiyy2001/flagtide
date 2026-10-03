package dev.flagwire.adapter.out.memory.usecase;

import dev.flagwire.adapter.out.memory.MemoryAdapters;
import dev.flagwire.application.security.Authorizer;
import dev.flagwire.application.security.Principal;
import dev.flagwire.application.testing.MutableTimeSource;
import dev.flagwire.application.testing.Samples;
import dev.flagwire.application.testing.SequentialIds;
import dev.flagwire.application.usecase.ArchiveFlag;
import dev.flagwire.application.usecase.Authenticate;
import dev.flagwire.application.usecase.BuildSnapshot;
import dev.flagwire.application.usecase.ConfigureFlag;
import dev.flagwire.application.usecase.CreateEnvironment;
import dev.flagwire.application.usecase.CreateFlag;
import dev.flagwire.application.usecase.CreateProject;
import dev.flagwire.application.usecase.DeleteSegment;
import dev.flagwire.application.usecase.EngageKillSwitch;
import dev.flagwire.application.usecase.GetFlag;
import dev.flagwire.application.usecase.GetProject;
import dev.flagwire.application.usecase.IssuedKey;
import dev.flagwire.application.usecase.ListApiKeys;
import dev.flagwire.application.usecase.ListFlags;
import dev.flagwire.application.usecase.ListSegments;
import dev.flagwire.application.usecase.ProvisionedProject;
import dev.flagwire.application.usecase.ReadAuditLog;
import dev.flagwire.application.usecase.ReleaseKillSwitch;
import dev.flagwire.application.usecase.SaveSegment;
import dev.flagwire.application.usecase.SyncSince;
import dev.flagwire.application.usecase.ToggleFlag;
import dev.flagwire.application.usecase.UpdateFlagDefinition;
import dev.flagwire.domain.access.ApiKeyKind;
import dev.flagwire.domain.error.FlagwireError;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.ProjectKey;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

final class Flagwire {

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

  Flagwire() {
    this(MemoryAdapters.DEFAULT_RETENTION);
  }

  Flagwire(int changeLogRetention) {
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

  FlagwireError failure(ThrowingCallable work) {
    try {
      work.call();
    } catch (FlagwireException failure) {
      return failure.error();
    } catch (Throwable other) {
      throw new AssertionError("expected a FlagwireException but got " + other, other);
    }
    throw new AssertionError("expected a FlagwireException but nothing was thrown");
  }

  private IssuedKey issued(EnvironmentKey environment, ApiKeyKind kind) {
    return this.provisioned.keys().stream()
        .filter(key -> key.key().environment().equals(environment) && key.key().kind() == kind)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no " + kind + " key for " + environment));
  }
}
