package dev.flagwire.adapter.memory.usecase;

import static dev.flagwire.adapter.memory.usecase.Flagwire.DEV;
import static org.assertj.core.api.Assertions.assertThat;

import dev.flagwire.application.contract.Concurrently;
import dev.flagwire.application.contract.Concurrently.Outcome;
import dev.flagwire.application.security.Principal;
import dev.flagwire.application.usecase.CommandResult;
import dev.flagwire.application.usecase.CreateFlag;
import dev.flagwire.application.usecase.ToggleFlag;
import dev.flagwire.domain.error.FlagwireError;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.evaluation.FlagType;
import dev.flagwire.domain.flag.Flag;
import dev.flagwire.domain.value.EnvironmentVersion;
import dev.flagwire.domain.value.FlagKey;
import dev.flagwire.domain.value.Revision;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConcurrencyTest {

  private final Flagwire app = new Flagwire();
  private final Principal admin = this.app.admin(DEV);

  @Test
  void onlyOneOfManyEditsBasedOnTheSameRevisionWins() {
    Fixtures.createFlag(this.app, "checkout");

    List<Outcome<CommandResult<Flag>>> outcomes =
        Concurrently.run(
            8,
            () ->
                this.app.toggleFlag.execute(
                    this.admin,
                    new ToggleFlag.Command(
                        new FlagKey("checkout"), Optional.of(Revision.FIRST), DEV, true)));

    assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
    assertThat(outcomes)
        .filteredOn(outcome -> !outcome.succeeded())
        .hasSize(7)
        .allSatisfy(
            outcome ->
                assertThat(((FlagwireException) outcome.failure()).error())
                    .isInstanceOf(FlagwireError.Conflict.class));
    assertThat(this.app.adapters.changeLog().currentVersion(this.admin.environmentRef()))
        .isEqualTo(EnvironmentVersion.of(2));
  }

  @Test
  void onlyOneOfManyCreationsOfTheSameFlagWins() {
    List<Outcome<CommandResult<Flag>>> outcomes =
        Concurrently.run(
            8, () -> this.app.createFlag.execute(this.admin, Fixtures.booleanFlag("checkout")));

    assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
    assertThat(this.app.adapters.changeLog().currentVersion(this.admin.environmentRef()))
        .isEqualTo(EnvironmentVersion.of(1));
  }

  @Test
  void concurrentCreationsOfDifferentFlagsGetGaplessVersions() {
    AtomicInteger counter = new AtomicInteger();

    Concurrently.run(
        8,
        () ->
            this.app.createFlag.execute(
                this.admin,
                new CreateFlag.Command(
                    new FlagKey("flag-" + counter.incrementAndGet()),
                    "d",
                    FlagType.BOOLEAN,
                    Fixtures.booleanVariants(),
                    Fixtures.OFF,
                    Fixtures.OFF)));

    assertThat(this.app.adapters.changeLog().currentVersion(this.admin.environmentRef()))
        .isEqualTo(EnvironmentVersion.of(8));
    assertThat(
            this.app
                .adapters
                .changeLog()
                .entriesAfter(this.admin.environmentRef(), EnvironmentVersion.ZERO))
        .extracting(entry -> entry.version().value())
        .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
  }
}
