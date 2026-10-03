package dev.flagwire.adapter.memory.usecase;

import static dev.flagwire.adapter.memory.usecase.Flagwire.DEV;
import static dev.flagwire.adapter.memory.usecase.Flagwire.PROD;
import static org.assertj.core.api.Assertions.assertThat;

import dev.flagwire.application.change.Change;
import dev.flagwire.application.security.Principal;
import dev.flagwire.application.sync.Snapshot;
import dev.flagwire.application.sync.SyncResult;
import dev.flagwire.application.usecase.ArchiveFlag;
import dev.flagwire.application.usecase.DeleteSegment;
import dev.flagwire.application.usecase.ToggleFlag;
import dev.flagwire.domain.value.EnvironmentVersion;
import dev.flagwire.domain.value.FlagKey;
import dev.flagwire.domain.value.SegmentKey;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SyncUseCasesTest {

  private final Flagwire app = new Flagwire();
  private final Principal sdk = this.app.sdk(DEV);

  private void toggle(String key, boolean enabled) {
    this.app.toggleFlag.execute(
        this.app.admin(DEV),
        new ToggleFlag.Command(new FlagKey(key), Optional.empty(), DEV, enabled));
  }

  private SyncResult sync(long since) {
    return this.app.syncSince.execute(this.sdk, Optional.of(EnvironmentVersion.of(since)));
  }

  @Test
  void anEmptyEnvironmentHasAnEmptySnapshotAtVersionZero() {
    Snapshot snapshot = this.app.buildSnapshot.execute(this.sdk);

    assertThat(snapshot.version()).isEqualTo(EnvironmentVersion.ZERO);
    assertThat(snapshot.committedAt()).isEmpty();
    assertThat(snapshot.flags()).isEmpty();
    assertThat(snapshot.segments()).isEmpty();
  }

  @Test
  void aSnapshotHoldsTheCompiledFlagsAndSegmentsOfItsEnvironmentOnly() {
    Fixtures.createFlag(this.app, "checkout");
    Fixtures.createFlag(this.app, "search");
    this.app.saveSegment.execute(this.app.admin(DEV), Fixtures.betaTesters(DEV));
    this.toggle("checkout", true);
    this.app.time.advance(Duration.ofMinutes(1));
    this.toggle("search", true);

    Snapshot dev = this.app.buildSnapshot.execute(this.sdk);
    Snapshot prod = this.app.buildSnapshot.execute(this.app.sdk(PROD));

    assertThat(dev.version()).isEqualTo(EnvironmentVersion.of(5));
    assertThat(dev.committedAt()).contains(this.app.time.now());
    assertThat(dev.flags())
        .extracting(config -> config.key())
        .containsExactly("checkout", "search");
    assertThat(dev.flags()).allMatch(config -> config.enabled());
    assertThat(dev.segments()).extracting(segment -> segment.key()).containsExactly("beta");
    assertThat(prod.version()).isEqualTo(EnvironmentVersion.of(2));
    assertThat(prod.flags()).noneMatch(config -> config.enabled());
    assertThat(prod.segments()).isEmpty();
  }

  @Test
  void withoutAKnownVersionTheClientGetsASnapshot() {
    Fixtures.createFlag(this.app, "checkout");

    SyncResult result = this.app.syncSince.execute(this.sdk, Optional.empty());

    assertThat(result).isInstanceOf(SyncResult.FullSnapshot.class);
  }

  @Test
  void aClientThatIsUpToDateGetsAnEmptyDelta() {
    Fixtures.createFlag(this.app, "checkout");

    SyncResult result = this.sync(1);

    assertThat(result)
        .isInstanceOfSatisfying(
            SyncResult.Deltas.class,
            deltas -> {
              assertThat(deltas.entries()).isEmpty();
              assertThat(deltas.from()).isEqualTo(EnvironmentVersion.of(1));
              assertThat(deltas.to()).isEqualTo(EnvironmentVersion.of(1));
            });
  }

  @Test
  void aClientBehindGetsEveryMissedVersionInOrder() {
    Fixtures.createFlag(this.app, "checkout");
    this.toggle("checkout", true);
    this.toggle("checkout", false);

    SyncResult result = this.sync(1);

    assertThat(result)
        .isInstanceOfSatisfying(
            SyncResult.Deltas.class,
            deltas -> {
              assertThat(deltas.from()).isEqualTo(EnvironmentVersion.of(1));
              assertThat(deltas.to()).isEqualTo(EnvironmentVersion.of(3));
              assertThat(deltas.entries())
                  .extracting(entry -> entry.version().value())
                  .containsExactly(2L, 3L);
              assertThat(deltas.entries().get(0).changes())
                  .singleElement()
                  .isInstanceOfSatisfying(
                      Change.FlagUpserted.class,
                      upsert -> assertThat(upsert.config().enabled()).isTrue());
            });
  }

  @Test
  void aClientAheadOfTheServerGetsASnapshot() {
    Fixtures.createFlag(this.app, "checkout");

    assertThat(this.sync(99)).isInstanceOf(SyncResult.FullSnapshot.class);
  }

  @Test
  void aClientOlderThanTheRetainedLogGetsASnapshot() {
    Flagwire shortLived = new Flagwire(3);
    Principal reader = shortLived.sdk(DEV);
    shortLived.createFlag.execute(shortLived.admin(DEV), Fixtures.booleanFlag("checkout"));
    for (int index = 0; index < 6; index++) {
      shortLived.toggleFlag.execute(
          shortLived.admin(DEV),
          new ToggleFlag.Command(new FlagKey("checkout"), Optional.empty(), DEV, index % 2 == 0));
    }

    SyncResult stale = shortLived.syncSince.execute(reader, Optional.of(EnvironmentVersion.of(1)));
    SyncResult recent = shortLived.syncSince.execute(reader, Optional.of(EnvironmentVersion.of(5)));

    assertThat(stale)
        .isInstanceOfSatisfying(
            SyncResult.FullSnapshot.class,
            full -> assertThat(full.snapshot().version()).isEqualTo(EnvironmentVersion.of(7)));
    assertThat(recent).isInstanceOf(SyncResult.Deltas.class);
  }

  @Test
  void anAdminKeyCanAlsoSyncItsOwnEnvironment() {
    Fixtures.createFlag(this.app, "checkout");

    SyncResult result =
        this.app.syncSince.execute(this.app.admin(DEV), Optional.of(EnvironmentVersion.ZERO));

    assertThat(result).isInstanceOf(SyncResult.Deltas.class);
  }

  @Test
  void archivedFlagsArriveAsRemovals() {
    Fixtures.createFlag(this.app, "checkout");
    this.app.archiveFlag.execute(
        this.app.admin(DEV), new ArchiveFlag.Command(new FlagKey("checkout"), Optional.empty()));

    SyncResult result = this.sync(1);

    assertThat(result)
        .isInstanceOfSatisfying(
            SyncResult.Deltas.class,
            deltas ->
                assertThat(deltas.entries().get(0).changes())
                    .containsExactly(new Change.FlagRemoved("checkout")));
  }

  @Test
  void deletedSegmentsArriveAsRemovals() {
    this.app.saveSegment.execute(this.app.admin(DEV), Fixtures.betaTesters(DEV));
    this.app.deleteSegment.execute(
        this.app.admin(DEV),
        new DeleteSegment.Command(DEV, new SegmentKey("beta"), Optional.empty()));

    SyncResult result = this.sync(1);

    assertThat(result)
        .isInstanceOfSatisfying(
            SyncResult.Deltas.class,
            deltas ->
                assertThat(deltas.entries().get(0).changes())
                    .containsExactly(new Change.SegmentRemoved("beta")));
  }
}
