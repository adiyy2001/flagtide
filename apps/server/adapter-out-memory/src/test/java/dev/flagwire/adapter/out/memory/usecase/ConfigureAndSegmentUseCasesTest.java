package dev.flagwire.adapter.out.memory.usecase;

import static dev.flagwire.adapter.out.memory.usecase.Fixtures.OFF;
import static dev.flagwire.adapter.out.memory.usecase.Fixtures.ON;
import static dev.flagwire.adapter.out.memory.usecase.Flagwire.DEV;
import static dev.flagwire.adapter.out.memory.usecase.Flagwire.PROD;
import static org.assertj.core.api.Assertions.assertThat;

import dev.flagwire.application.security.Principal;
import dev.flagwire.application.usecase.ArchiveFlag;
import dev.flagwire.application.usecase.CommandResult;
import dev.flagwire.application.usecase.ConfigureFlag;
import dev.flagwire.application.usecase.DeleteSegment;
import dev.flagwire.application.usecase.ReadAuditLog;
import dev.flagwire.application.usecase.SaveSegment;
import dev.flagwire.application.usecase.ToggleFlag;
import dev.flagwire.domain.audit.AuditEntry;
import dev.flagwire.domain.audit.EntityType;
import dev.flagwire.domain.error.FlagwireError;
import dev.flagwire.domain.event.DomainEvent;
import dev.flagwire.domain.flag.EnvironmentSettings;
import dev.flagwire.domain.flag.Flag;
import dev.flagwire.domain.flag.RolloutEntry;
import dev.flagwire.domain.flag.Serving;
import dev.flagwire.domain.segment.Segment;
import dev.flagwire.domain.value.EnvironmentVersion;
import dev.flagwire.domain.value.FlagKey;
import dev.flagwire.domain.value.Revision;
import dev.flagwire.domain.value.SegmentKey;
import dev.flagwire.domain.value.Weight;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigureAndSegmentUseCasesTest {

  private static final FlagKey CHECKOUT = new FlagKey("checkout");
  private static final SegmentKey BETA = new SegmentKey("beta");

  private final Flagwire app = new Flagwire();
  private final Principal admin = this.app.admin(DEV);

  private CommandResult<Flag> configure(EnvironmentSettings settings, Optional<Revision> expected) {
    return this.app.configureFlag.execute(
        this.admin, new ConfigureFlag.Command(CHECKOUT, expected, DEV, settings));
  }

  @Test
  void savingANewSegmentEmitsACreatedEventAndAdvancesItsEnvironment() {
    CommandResult<Segment> result =
        this.app.saveSegment.execute(this.admin, Fixtures.betaTesters(DEV));

    assertThat(result.events()).singleElement().isInstanceOf(DomainEvent.SegmentSaved.class);
    assertThat(((DomainEvent.SegmentSaved) result.events().get(0)).created()).isTrue();
    assertThat(result.versions()).containsOnlyKeys(DEV);
    assertThat(result.value().revision()).isEqualTo(Revision.FIRST);
    assertThat(this.app.listSegments.execute(this.admin, DEV)).hasSize(1);
    assertThat(this.app.listSegments.execute(this.app.admin(PROD), PROD)).isEmpty();
  }

  @Test
  void updatingASegmentBumpsItsRevisionAndEmitsAnUpdateEvent() {
    this.app.saveSegment.execute(this.admin, Fixtures.betaTesters(DEV));

    CommandResult<Segment> result =
        this.app.saveSegment.execute(
            this.admin,
            new SaveSegment.Command(
                DEV,
                BETA,
                Optional.of(Revision.FIRST),
                "Beta testers",
                Set.of("u1", "u2"),
                Set.of(),
                List.of()));

    assertThat(((DomainEvent.SegmentSaved) result.events().get(0)).created()).isFalse();
    assertThat(result.value().revision()).isEqualTo(Revision.of(2));
    assertThat(result.value().included()).containsExactlyInAnyOrder("u1", "u2");
    assertThat(result.versions().get(DEV)).isEqualTo(EnvironmentVersion.of(2));
  }

  @Test
  void savingAnIdenticalSegmentChangesNothing() {
    this.app.saveSegment.execute(this.admin, Fixtures.betaTesters(DEV));

    CommandResult<Segment> result =
        this.app.saveSegment.execute(this.admin, Fixtures.betaTesters(DEV));

    assertThat(result.changed()).isFalse();
    assertThat(result.value().revision()).isEqualTo(Revision.FIRST);
    assertThat(this.app.adapters.changeLog().currentVersion(this.admin.environmentRef()))
        .isEqualTo(EnvironmentVersion.of(1));
  }

  @Test
  void aStaleSegmentRevisionConflicts() {
    this.app.saveSegment.execute(this.admin, Fixtures.segment(DEV, "beta", "u1"));
    this.app.saveSegment.execute(this.admin, Fixtures.segment(DEV, "beta", "u2"));

    assertThat(
            this.app.failure(
                () ->
                    this.app.saveSegment.execute(
                        this.admin,
                        new SaveSegment.Command(
                            DEV,
                            BETA,
                            Optional.of(Revision.FIRST),
                            "Segment beta",
                            Set.of("u3"),
                            Set.of(),
                            List.of()))))
        .isInstanceOf(FlagwireError.Conflict.class);
  }

  @Test
  void segmentsAreWrittenOnlyByAnAdminOfTheirEnvironment() {
    assertThat(
            this.app.failure(
                () -> this.app.saveSegment.execute(this.app.sdk(DEV), Fixtures.betaTesters(DEV))))
        .isInstanceOf(FlagwireError.Forbidden.class);
    assertThat(
            this.app.failure(
                () -> this.app.saveSegment.execute(this.admin, Fixtures.betaTesters(PROD))))
        .isInstanceOf(FlagwireError.Forbidden.class);
  }

  @Test
  void anInvalidSegmentIsRejected() {
    SaveSegment.Command overlapping =
        new SaveSegment.Command(
            DEV, BETA, Optional.empty(), "Beta", Set.of("u1"), Set.of("u1"), List.of());

    assertThat(this.app.failure(() -> this.app.saveSegment.execute(this.admin, overlapping)))
        .isInstanceOf(FlagwireError.ValidationFailed.class);
    assertThat(this.app.listSegments.execute(this.admin, DEV)).isEmpty();
  }

  @Test
  void segmentChangesAreAuditedWithBeforeAndAfter() {
    this.app.saveSegment.execute(this.admin, Fixtures.segment(DEV, "beta", "u1"));
    this.app.saveSegment.execute(this.admin, Fixtures.segment(DEV, "beta", "u1", "u2"));
    this.app.deleteSegment.execute(
        this.admin, new DeleteSegment.Command(DEV, BETA, Optional.empty()));

    List<AuditEntry> entries =
        this.app.readAuditLog.execute(this.admin, ReadAuditLog.Query.newest(10)).stream()
            .filter(entry -> entry.entityType() == EntityType.SEGMENT)
            .toList();

    assertThat(entries)
        .extracting(AuditEntry::action)
        .containsExactly("SegmentDeleted", "SegmentSaved", "SegmentSaved");
    assertThat(entries.get(2).before()).isEmpty();
    assertThat(entries.get(2).after()).isPresent();
    assertThat(entries.get(1).before()).isPresent();
    assertThat(entries.get(1).after()).isPresent();
    assertThat(entries.get(0).before()).isPresent();
    assertThat(entries.get(0).after()).isEmpty();
    assertThat(entries).extracting(AuditEntry::author).containsOnly("dev-admin");
  }

  @Test
  void configuringARolloutRuleEmitsOneEventAndRecordsBeforeAndAfter() {
    Fixtures.createFlag(this.app, "checkout");
    this.app.saveSegment.execute(this.admin, Fixtures.betaTesters(DEV));

    CommandResult<Flag> result =
        this.configure(
            Fixtures.rolloutBehindSegment("beta", 25_000, true), Optional.of(Revision.FIRST));

    assertThat(result.events()).singleElement().isInstanceOf(DomainEvent.FlagConfigChanged.class);
    assertThat(result.versions()).containsOnlyKeys(DEV);
    assertThat(result.value().requireEnvironment(DEV).rules()).hasSize(1);
    assertThat(result.value().requireEnvironment(DEV).enabled()).isTrue();
    assertThat(result.value().requireEnvironment(PROD).rules()).isEmpty();
    AuditEntry entry =
        this.app.readAuditLog.execute(this.admin, ReadAuditLog.Query.newest(1)).get(0);
    assertThat(entry.action()).isEqualTo("FlagConfigChanged");
    assertThat(entry.before()).isPresent();
    assertThat(entry.after()).isPresent();
    assertThat(entry.before()).isNotEqualTo(entry.after());
  }

  @Test
  void aRuleMayNotReferenceASegmentThatDoesNotExistInThatEnvironment() {
    Fixtures.createFlag(this.app, "checkout");
    this.app.saveSegment.execute(this.app.admin(PROD), Fixtures.betaTesters(PROD));

    assertThat(
            this.app.failure(
                () ->
                    this.configure(
                        Fixtures.rolloutBehindSegment("beta", 25_000, true), Optional.empty())))
        .isInstanceOf(FlagwireError.ValidationFailed.class);
    assertThat(this.app.getFlag.execute(this.admin, CHECKOUT).requireEnvironment(DEV).rules())
        .isEmpty();
  }

  @Test
  void aRolloutMustSumToTheWholeRange() {
    Fixtures.createFlag(this.app, "checkout");

    assertThat(
            this.app.failure(
                () ->
                    new Serving.Rollout(
                        List.of(
                            new RolloutEntry(ON, Weight.of(60_000)),
                            new RolloutEntry(OFF, Weight.of(30_000))))))
        .isInstanceOf(FlagwireError.ValidationFailed.class);
  }

  @Test
  void configuringWithAStaleRevisionConflicts() {
    Fixtures.createFlag(this.app, "checkout");
    this.app.toggleFlag.execute(
        this.admin, new ToggleFlag.Command(CHECKOUT, Optional.empty(), DEV, true));

    assertThat(
            this.app.failure(
                () ->
                    this.configure(
                        new EnvironmentSettings(true, OFF, List.of(), Serving.fixed(ON)),
                        Optional.of(Revision.FIRST))))
        .isInstanceOf(FlagwireError.Conflict.class);
  }

  @Test
  void configuringTheCurrentSettingsAgainChangesNothing() {
    Fixtures.createFlag(this.app, "checkout");
    EnvironmentSettings settings = new EnvironmentSettings(true, OFF, List.of(), Serving.fixed(ON));
    this.configure(settings, Optional.empty());

    CommandResult<Flag> again = this.configure(settings, Optional.empty());

    assertThat(again.changed()).isFalse();
    assertThat(again.versions()).isEmpty();
  }

  @Test
  void aSegmentInUseCannotBeDeletedAndTheErrorNamesTheFlag() {
    Fixtures.createFlag(this.app, "checkout");
    this.app.saveSegment.execute(this.admin, Fixtures.betaTesters(DEV));
    this.configure(Fixtures.rolloutBehindSegment("beta", 25_000, true), Optional.empty());

    FlagwireError error =
        this.app.failure(
            () ->
                this.app.deleteSegment.execute(
                    this.admin, new DeleteSegment.Command(DEV, BETA, Optional.empty())));

    assertThat(error).isInstanceOf(FlagwireError.Conflict.class);
    assertThat(error.toString()).contains("checkout");
    assertThat(this.app.listSegments.execute(this.admin, DEV)).hasSize(1);
  }

  @Test
  void aSegmentCanBeDeletedOnceNoRuleUsesItOrTheFlagIsArchived() {
    Fixtures.createFlag(this.app, "checkout");
    this.app.saveSegment.execute(this.admin, Fixtures.betaTesters(DEV));
    this.configure(Fixtures.rolloutBehindSegment("beta", 25_000, true), Optional.empty());
    this.configure(
        new EnvironmentSettings(true, OFF, List.of(), Serving.fixed(ON)), Optional.empty());

    CommandResult<SegmentKey> deleted =
        this.app.deleteSegment.execute(
            this.admin, new DeleteSegment.Command(DEV, BETA, Optional.empty()));

    assertThat(deleted.events()).singleElement().isInstanceOf(DomainEvent.SegmentDeleted.class);
    assertThat(deleted.value()).isEqualTo(BETA);
    assertThat(this.app.listSegments.execute(this.admin, DEV)).isEmpty();

    this.app.saveSegment.execute(this.admin, Fixtures.betaTesters(DEV));
    this.configure(Fixtures.rolloutBehindSegment("beta", 25_000, true), Optional.empty());
    this.app.archiveFlag.execute(this.admin, new ArchiveFlag.Command(CHECKOUT, Optional.empty()));
    this.app.deleteSegment.execute(
        this.admin, new DeleteSegment.Command(DEV, BETA, Optional.empty()));
    assertThat(this.app.listSegments.execute(this.admin, DEV)).isEmpty();
  }

  @Test
  void aSegmentInUseInAnotherEnvironmentDoesNotBlockDeletionHere() {
    Fixtures.createFlag(this.app, "checkout");
    Principal prodAdmin = this.app.admin(PROD);
    this.app.saveSegment.execute(prodAdmin, Fixtures.betaTesters(PROD));
    this.app.configureFlag.execute(
        prodAdmin,
        new ConfigureFlag.Command(
            CHECKOUT, Optional.empty(), PROD, Fixtures.rolloutBehindSegment("beta", 10_000, true)));
    this.app.saveSegment.execute(this.admin, Fixtures.betaTesters(DEV));

    this.app.deleteSegment.execute(
        this.admin, new DeleteSegment.Command(DEV, BETA, Optional.empty()));

    assertThat(this.app.listSegments.execute(this.admin, DEV)).isEmpty();
    assertThat(this.app.listSegments.execute(prodAdmin, PROD)).hasSize(1);
  }

  @Test
  void deletingAMissingSegmentOrWithAStaleRevisionFails() {
    assertThat(
            this.app.failure(
                () ->
                    this.app.deleteSegment.execute(
                        this.admin, new DeleteSegment.Command(DEV, BETA, Optional.empty()))))
        .isInstanceOf(FlagwireError.NotFound.class);
    this.app.saveSegment.execute(this.admin, Fixtures.segment(DEV, "beta", "u1"));
    this.app.saveSegment.execute(this.admin, Fixtures.segment(DEV, "beta", "u2"));

    assertThat(
            this.app.failure(
                () ->
                    this.app.deleteSegment.execute(
                        this.admin,
                        new DeleteSegment.Command(DEV, BETA, Optional.of(Revision.FIRST)))))
        .isInstanceOf(FlagwireError.Conflict.class);
    assertThat(
            this.app.failure(
                () ->
                    this.app.deleteSegment.execute(
                        this.app.sdk(DEV), new DeleteSegment.Command(DEV, BETA, Optional.empty()))))
        .isInstanceOf(FlagwireError.Forbidden.class);
  }
}
