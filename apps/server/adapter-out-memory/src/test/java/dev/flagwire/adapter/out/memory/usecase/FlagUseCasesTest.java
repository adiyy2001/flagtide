package dev.flagwire.adapter.out.memory.usecase;

import static dev.flagwire.adapter.out.memory.usecase.Fixtures.OFF;
import static dev.flagwire.adapter.out.memory.usecase.Fixtures.ON;
import static dev.flagwire.adapter.out.memory.usecase.Flagwire.DEV;
import static dev.flagwire.adapter.out.memory.usecase.Flagwire.PROD;
import static dev.flagwire.adapter.out.memory.usecase.Flagwire.STAGING;
import static org.assertj.core.api.Assertions.assertThat;

import dev.flagwire.application.security.Principal;
import dev.flagwire.application.usecase.ArchiveFlag;
import dev.flagwire.application.usecase.CommandResult;
import dev.flagwire.application.usecase.CreateFlag;
import dev.flagwire.application.usecase.CreateProject;
import dev.flagwire.application.usecase.EngageKillSwitch;
import dev.flagwire.application.usecase.ListFlags;
import dev.flagwire.application.usecase.ReadAuditLog;
import dev.flagwire.application.usecase.ReleaseKillSwitch;
import dev.flagwire.application.usecase.ToggleFlag;
import dev.flagwire.application.usecase.UpdateFlagDefinition;
import dev.flagwire.domain.access.ApiKeyKind;
import dev.flagwire.domain.audit.AuditEntry;
import dev.flagwire.domain.audit.EntityType;
import dev.flagwire.domain.error.FlagwireError;
import dev.flagwire.domain.evaluation.FlagType;
import dev.flagwire.domain.evaluation.JsonValue;
import dev.flagwire.domain.event.DomainEvent;
import dev.flagwire.domain.flag.Flag;
import dev.flagwire.domain.flag.FlagVariant;
import dev.flagwire.domain.value.EnvironmentVersion;
import dev.flagwire.domain.value.FlagKey;
import dev.flagwire.domain.value.ProjectKey;
import dev.flagwire.domain.value.Revision;
import dev.flagwire.domain.value.VariantKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FlagUseCasesTest {

  private static final FlagKey CHECKOUT = new FlagKey("checkout");

  private final Flagwire app = new Flagwire();
  private final Principal admin = this.app.admin(DEV);

  private List<AuditEntry> audit() {
    return this.app.readAuditLog.execute(this.admin, ReadAuditLog.Query.newest(100));
  }

  private List<AuditEntry> flagAudit() {
    return this.audit().stream().filter(entry -> entry.entityType() == EntityType.FLAG).toList();
  }

  @Test
  void creatingAFlagEmitsOneEventAndAdvancesEveryEnvironment() {
    CommandResult<Flag> result =
        this.app.createFlag.execute(this.admin, Fixtures.booleanFlag("checkout"));

    assertThat(result.events()).singleElement().isInstanceOf(DomainEvent.FlagCreated.class);
    assertThat(result.versions().keySet()).containsExactlyInAnyOrder(DEV, STAGING, PROD);
    assertThat(result.versions().values()).containsOnly(EnvironmentVersion.of(1));
    assertThat(result.value().revision()).isEqualTo(Revision.FIRST);
    assertThat(result.value().environments()).hasSize(3);
  }

  @Test
  void everyEnvironmentGetsItsOwnSaltAndStartsDisabled() {
    Flag flag = Fixtures.createFlag(this.app, "checkout");

    assertThat(
            List.of(DEV, STAGING, PROD).stream()
                .map(environment -> flag.requireEnvironment(environment).salt())
                .distinct())
        .hasSize(3);
    assertThat(flag.requireEnvironment(DEV).enabled()).isFalse();
    assertThat(flag.requireEnvironment(DEV).killSwitch()).isFalse();
  }

  @Test
  void creatingAFlagWritesOneAuditEntryPerEnvironmentWithAuthorAndTimestamp() {
    this.app.time.advance(Duration.ofMinutes(5));
    Fixtures.createFlag(this.app, "checkout");

    List<AuditEntry> entries = this.flagAudit();

    assertThat(entries).hasSize(3);
    assertThat(entries).extracting(AuditEntry::author).containsOnly("dev-admin");
    assertThat(entries).extracting(AuditEntry::action).containsOnly("FlagCreated");
    assertThat(entries).extracting(AuditEntry::at).containsOnly(this.app.time.now());
    assertThat(entries).extracting(AuditEntry::before).containsOnly(Optional.empty());
    assertThat(entries).allSatisfy(entry -> assertThat(entry.after()).isPresent());
    assertThat(entries)
        .extracting(entry -> entry.environmentVersion().orElseThrow())
        .containsOnly(EnvironmentVersion.of(1));
  }

  @Test
  void aSecondFlagWithTheSameKeyConflicts() {
    Fixtures.createFlag(this.app, "checkout");

    assertThat(this.app.failure(() -> Fixtures.createFlag(this.app, "checkout")))
        .isInstanceOf(FlagwireError.Conflict.class);
  }

  @Test
  void anInvalidDefinitionIsRejectedAndNothingIsRecorded() {
    CreateFlag.Command invalid =
        new CreateFlag.Command(
            CHECKOUT,
            "broken",
            FlagType.BOOLEAN,
            Fixtures.booleanVariants(),
            OFF,
            new VariantKey("missing"));

    assertThat(this.app.failure(() -> this.app.createFlag.execute(this.admin, invalid)))
        .isInstanceOf(FlagwireError.ValidationFailed.class);
    assertThat(this.app.listFlags.execute(this.admin, ListFlags.Query.everything())).isEmpty();
    assertThat(this.app.adapters.changeLog().currentVersion(this.admin.environmentRef()))
        .isEqualTo(EnvironmentVersion.ZERO);
  }

  @Test
  void anSdkKeyCannotCreateOrEditFlags() {
    Principal sdk = this.app.sdk(DEV);
    Fixtures.createFlag(this.app, "checkout");

    assertThat(this.app.failure(() -> this.app.createFlag.execute(sdk, Fixtures.booleanFlag("x"))))
        .isInstanceOf(FlagwireError.Forbidden.class);
    assertThat(
            this.app.failure(
                () ->
                    this.app.toggleFlag.execute(
                        sdk, new ToggleFlag.Command(CHECKOUT, Optional.empty(), DEV, true))))
        .isInstanceOf(FlagwireError.Forbidden.class);
    assertThat(
            this.app.failure(
                () ->
                    this.app.archiveFlag.execute(
                        sdk, new ArchiveFlag.Command(CHECKOUT, Optional.empty()))))
        .isInstanceOf(FlagwireError.Forbidden.class);
  }

  @Test
  void updatingTheDefinitionEmitsOneEventAndAdvancesEveryEnvironment() {
    Fixtures.createFlag(this.app, "checkout");

    CommandResult<Flag> result =
        this.app.updateFlagDefinition.execute(
            this.admin,
            new UpdateFlagDefinition.Command(
                CHECKOUT, Optional.of(Revision.FIRST), "new words", Fixtures.booleanVariants()));

    assertThat(result.events())
        .singleElement()
        .isInstanceOf(DomainEvent.FlagDefinitionChanged.class);
    assertThat(result.value().description()).isEqualTo("new words");
    assertThat(result.value().revision()).isEqualTo(Revision.of(2));
    assertThat(result.versions().values()).containsOnly(EnvironmentVersion.of(2));
  }

  @Test
  void updatingWithTheSameDefinitionChangesNothing() {
    Fixtures.createFlag(this.app, "checkout");
    int auditBefore = this.audit().size();

    CommandResult<Flag> result =
        this.app.updateFlagDefinition.execute(
            this.admin,
            new UpdateFlagDefinition.Command(
                CHECKOUT, Optional.empty(), "the checkout flag", Fixtures.booleanVariants()));

    assertThat(result.changed()).isFalse();
    assertThat(result.versions()).isEmpty();
    assertThat(result.value().revision()).isEqualTo(Revision.FIRST);
    assertThat(this.audit()).hasSize(auditBefore);
  }

  @Test
  void aStaleExpectedRevisionConflictsAndChangesNothing() {
    Fixtures.createFlag(this.app, "checkout");
    this.app.toggleFlag.execute(
        this.admin, new ToggleFlag.Command(CHECKOUT, Optional.of(Revision.FIRST), DEV, true));

    FlagwireError error =
        this.app.failure(
            () ->
                this.app.updateFlagDefinition.execute(
                    this.admin,
                    new UpdateFlagDefinition.Command(
                        CHECKOUT,
                        Optional.of(Revision.FIRST),
                        "late edit",
                        Fixtures.booleanVariants())));

    assertThat(error).isInstanceOf(FlagwireError.Conflict.class);
    assertThat(this.app.getFlag.execute(this.admin, CHECKOUT).description())
        .isEqualTo("the checkout flag");
  }

  @Test
  void aStaleRevisionOnAnUnchangedEditStillConflicts() {
    Fixtures.createFlag(this.app, "checkout");
    this.app.toggleFlag.execute(
        this.admin, new ToggleFlag.Command(CHECKOUT, Optional.empty(), DEV, true));

    assertThat(
            this.app.failure(
                () ->
                    this.app.toggleFlag.execute(
                        this.admin,
                        new ToggleFlag.Command(CHECKOUT, Optional.of(Revision.FIRST), DEV, true))))
        .isInstanceOf(FlagwireError.Conflict.class);
  }

  @Test
  void togglingEmitsOneEventAndAdvancesOnlyThatEnvironment() {
    Fixtures.createFlag(this.app, "checkout");

    CommandResult<Flag> result =
        this.app.toggleFlag.execute(
            this.admin, new ToggleFlag.Command(CHECKOUT, Optional.empty(), DEV, true));

    assertThat(result.events()).singleElement().isInstanceOf(DomainEvent.FlagToggled.class);
    assertThat(result.versions()).containsOnlyKeys(DEV);
    assertThat(result.versions().get(DEV)).isEqualTo(EnvironmentVersion.of(2));
    assertThat(this.app.adapters.changeLog().currentVersion(this.app.admin(PROD).environmentRef()))
        .isEqualTo(EnvironmentVersion.of(1));
    assertThat(result.value().requireEnvironment(DEV).enabled()).isTrue();
    assertThat(result.value().requireEnvironment(STAGING).enabled()).isFalse();
  }

  @Test
  void togglingToTheCurrentStateIsANoOp() {
    Fixtures.createFlag(this.app, "checkout");
    int auditBefore = this.audit().size();

    CommandResult<Flag> result =
        this.app.toggleFlag.execute(
            this.admin, new ToggleFlag.Command(CHECKOUT, Optional.empty(), DEV, false));

    assertThat(result.changed()).isFalse();
    assertThat(result.events()).isEmpty();
    assertThat(this.audit()).hasSize(auditBefore);
  }

  @Test
  void togglingRecordsBeforeAndAfterOfTheEnvironmentConfiguration() {
    Fixtures.createFlag(this.app, "checkout");
    this.app.time.advance(Duration.ofHours(1));
    this.app.toggleFlag.execute(
        this.admin, new ToggleFlag.Command(CHECKOUT, Optional.empty(), DEV, true));

    AuditEntry entry = this.flagAudit().get(0);

    assertThat(entry.action()).isEqualTo("FlagToggled");
    assertThat(entry.author()).isEqualTo("dev-admin");
    assertThat(entry.environment()).contains(DEV);
    assertThat(entry.at()).isEqualTo(this.app.time.now());
    assertThat(entry.before()).isPresent();
    assertThat(entry.after()).isPresent();
    assertThat(entry.before()).isNotEqualTo(entry.after());
    assertThat(entry.environmentVersion()).contains(EnvironmentVersion.of(2));
  }

  @Test
  void anAdminCannotChangeAnotherEnvironment() {
    Fixtures.createFlag(this.app, "checkout");

    assertThat(
            this.app.failure(
                () ->
                    this.app.toggleFlag.execute(
                        this.admin,
                        new ToggleFlag.Command(CHECKOUT, Optional.empty(), PROD, true))))
        .isInstanceOf(FlagwireError.Forbidden.class);
  }

  @Test
  void togglingAnUnknownFlagIsNotFound() {
    assertThat(
            this.app.failure(
                () ->
                    this.app.toggleFlag.execute(
                        this.admin, new ToggleFlag.Command(CHECKOUT, Optional.empty(), DEV, true))))
        .isInstanceOf(FlagwireError.NotFound.class);
  }

  @Test
  void theKillSwitchCanBeEngagedAndReleased() {
    Fixtures.createFlag(this.app, "checkout");

    CommandResult<Flag> engaged =
        this.app.engageKillSwitch.execute(this.admin, new EngageKillSwitch.Command(CHECKOUT, DEV));
    CommandResult<Flag> again =
        this.app.engageKillSwitch.execute(this.admin, new EngageKillSwitch.Command(CHECKOUT, DEV));
    CommandResult<Flag> released =
        this.app.releaseKillSwitch.execute(
            this.admin, new ReleaseKillSwitch.Command(CHECKOUT, DEV));

    assertThat(engaged.events()).singleElement().isInstanceOf(DomainEvent.KillSwitchEngaged.class);
    assertThat(engaged.value().requireEnvironment(DEV).killSwitch()).isTrue();
    assertThat(again.changed()).isFalse();
    assertThat(released.events())
        .singleElement()
        .isInstanceOf(DomainEvent.KillSwitchReleased.class);
    assertThat(released.value().requireEnvironment(DEV).killSwitch()).isFalse();
    assertThat(released.versions().get(DEV)).isEqualTo(EnvironmentVersion.of(3));
  }

  @Test
  void archivingEmitsOneEventRemovesTheFlagFromEveryEnvironmentAndHidesIt() {
    Fixtures.createFlag(this.app, "checkout");
    Fixtures.createFlag(this.app, "search");

    CommandResult<Flag> result =
        this.app.archiveFlag.execute(
            this.admin, new ArchiveFlag.Command(CHECKOUT, Optional.empty()));

    assertThat(result.events()).singleElement().isInstanceOf(DomainEvent.FlagArchived.class);
    assertThat(result.versions().keySet()).containsExactlyInAnyOrder(DEV, STAGING, PROD);
    assertThat(this.app.listFlags.execute(this.admin, ListFlags.Query.everything()))
        .extracting(flag -> flag.key().value())
        .containsExactly("search");
    assertThat(
            this.app.listFlags.execute(
                this.admin, new ListFlags.Query(Optional.empty(), Optional.empty(), true)))
        .hasSize(2);
    assertThat(this.app.buildSnapshot.execute(this.admin).flags())
        .extracting(config -> config.key())
        .containsExactly("search");
  }

  @Test
  void anArchivedFlagRejectsFurtherChanges() {
    Fixtures.createFlag(this.app, "checkout");
    this.app.archiveFlag.execute(this.admin, new ArchiveFlag.Command(CHECKOUT, Optional.empty()));

    assertThat(
            this.app.failure(
                () ->
                    this.app.toggleFlag.execute(
                        this.admin, new ToggleFlag.Command(CHECKOUT, Optional.empty(), DEV, true))))
        .isInstanceOf(FlagwireError.Conflict.class);
    assertThat(
            this.app.failure(
                () ->
                    this.app.archiveFlag.execute(
                        this.admin, new ArchiveFlag.Command(CHECKOUT, Optional.empty()))))
        .isInstanceOf(FlagwireError.Conflict.class);
  }

  @Test
  void everyChangeIsAuditedInOrderNewestFirst() {
    Fixtures.createFlag(this.app, "checkout");
    this.app.toggleFlag.execute(
        this.admin, new ToggleFlag.Command(CHECKOUT, Optional.empty(), DEV, true));
    this.app.engageKillSwitch.execute(this.admin, new EngageKillSwitch.Command(CHECKOUT, DEV));

    List<String> actions =
        new ArrayList<>(this.flagAudit().stream().map(AuditEntry::action).toList());

    assertThat(actions)
        .containsExactly(
            "KillSwitchEngaged", "FlagToggled", "FlagCreated", "FlagCreated", "FlagCreated");
  }

  @Test
  void listsFlagsFilteredByTextAndType() {
    Fixtures.createFlag(this.app, "checkout");
    Fixtures.createFlag(this.app, "search");
    this.app.createFlag.execute(
        this.admin,
        new CreateFlag.Command(
            new FlagKey("banner"),
            "Homepage banner text",
            FlagType.STRING,
            List.of(
                new FlagVariant(ON, new JsonValue.JsonString("hello")),
                new FlagVariant(OFF, new JsonValue.JsonString("bye"))),
            OFF,
            OFF));

    assertThat(this.app.listFlags.execute(this.admin, ListFlags.Query.everything()))
        .extracting(flag -> flag.key().value())
        .containsExactly("banner", "checkout", "search");
    assertThat(
            this.app.listFlags.execute(
                this.admin, new ListFlags.Query(Optional.of("CHECK"), Optional.empty(), false)))
        .extracting(flag -> flag.key().value())
        .containsExactly("checkout");
    assertThat(
            this.app.listFlags.execute(
                this.admin, new ListFlags.Query(Optional.of("homepage"), Optional.empty(), false)))
        .extracting(flag -> flag.key().value())
        .containsExactly("banner");
    assertThat(
            this.app.listFlags.execute(
                this.admin,
                new ListFlags.Query(Optional.empty(), Optional.of(FlagType.STRING), false)))
        .extracting(flag -> flag.key().value())
        .containsExactly("banner");
  }

  @Test
  void readingAnUnknownFlagIsNotFoundAndNeedsAnAdmin() {
    assertThat(this.app.failure(() -> this.app.getFlag.execute(this.admin, CHECKOUT)))
        .isInstanceOf(FlagwireError.NotFound.class);
    assertThat(this.app.failure(() -> this.app.getFlag.execute(this.app.sdk(DEV), CHECKOUT)))
        .isInstanceOf(FlagwireError.Forbidden.class);
    assertThat(
            this.app.failure(
                () -> this.app.listFlags.execute(this.app.sdk(DEV), ListFlags.Query.everything())))
        .isInstanceOf(FlagwireError.Forbidden.class);
  }

  @Test
  void aFlagCreatedByOneProjectIsInvisibleToAnother() {
    Fixtures.createFlag(this.app, "checkout");
    var blog =
        this.app.createProject.execute(
            new CreateProject.Command(new ProjectKey("blog"), "Blog", "ann"));
    Principal blogAdmin =
        this.app.authenticate.execute(
            blog.value().keys().stream()
                .filter(issued -> issued.key().kind() == ApiKeyKind.ADMIN)
                .filter(issued -> issued.key().environment().equals(DEV))
                .findFirst()
                .orElseThrow()
                .secret());

    assertThat(this.app.listFlags.execute(blogAdmin, ListFlags.Query.everything())).isEmpty();
    assertThat(this.app.failure(() -> this.app.getFlag.execute(blogAdmin, CHECKOUT)))
        .isInstanceOf(FlagwireError.NotFound.class);
  }

  @Test
  void auditReadingValidatesPagingAndNeedsAnAdmin() {
    assertThat(
            this.app.failure(
                () -> this.app.readAuditLog.execute(this.admin, ReadAuditLog.Query.newest(0))))
        .isInstanceOf(FlagwireError.ValidationFailed.class);
    assertThat(
            this.app.failure(
                () -> this.app.readAuditLog.execute(this.admin, ReadAuditLog.Query.newest(501))))
        .isInstanceOf(FlagwireError.ValidationFailed.class);
    assertThat(
            this.app.failure(
                () ->
                    this.app.readAuditLog.execute(
                        this.admin, ReadAuditLog.Query.newest(10).skipping(-1))))
        .isInstanceOf(FlagwireError.ValidationFailed.class);
    assertThat(
            this.app.failure(
                () ->
                    this.app.readAuditLog.execute(
                        this.app.sdk(DEV), ReadAuditLog.Query.newest(10))))
        .isInstanceOf(FlagwireError.Forbidden.class);
  }
}
