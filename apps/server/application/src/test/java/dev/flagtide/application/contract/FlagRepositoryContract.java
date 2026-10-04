package dev.flagtide.application.contract;

import static dev.flagtide.application.testing.Samples.BLOG;
import static dev.flagtide.application.testing.Samples.SHOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagtide.application.testing.Samples;
import dev.flagtide.domain.error.FlagtideError;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.flag.Flag;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.Revision;
import java.util.List;
import org.junit.jupiter.api.Test;

public abstract class FlagRepositoryContract extends AdapterContract {

  private static void assertConflict(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(FlagtideException.class)
        .extracting(failure -> ((FlagtideException) failure).error())
        .isInstanceOf(FlagtideError.Conflict.class);
  }

  @Test
  void savesAndFindsAFlag() {
    Flag flag = Samples.flag("checkout");

    this.adapters.flags().save(SHOP, flag, Revision.NONE);

    assertThat(this.adapters.flags().find(SHOP, flag.key())).contains(flag);
  }

  @Test
  void findsNothingForAnUnknownKeyOrProject() {
    this.adapters.flags().save(SHOP, Samples.flag("checkout"), Revision.NONE);

    assertThat(this.adapters.flags().find(SHOP, new FlagKey("other"))).isEmpty();
    assertThat(this.adapters.flags().find(BLOG, new FlagKey("checkout"))).isEmpty();
  }

  @Test
  void listsTheFlagsOfOneProjectOnly() {
    this.adapters.flags().save(SHOP, Samples.flag("a"), Revision.NONE);
    this.adapters.flags().save(SHOP, Samples.flag("b"), Revision.NONE);
    this.adapters.flags().save(BLOG, Samples.flag("c"), Revision.NONE);

    assertThat(this.adapters.flags().findAll(SHOP))
        .extracting(flag -> flag.key().value())
        .containsExactlyInAnyOrder("a", "b");
    assertThat(this.adapters.flags().findAll(Samples.project("empty").key())).isEmpty();
  }

  @Test
  void acceptsAnUpdateBasedOnTheStoredRevision() {
    Flag first = Samples.flag("checkout");
    this.adapters.flags().save(SHOP, first, Revision.NONE);
    Flag second = Samples.nextRevision(first);

    this.adapters.flags().save(SHOP, second, first.revision());

    assertThat(this.adapters.flags().find(SHOP, first.key())).contains(second);
  }

  @Test
  void rejectsAnUpdateBasedOnAStaleRevision() {
    Flag first = Samples.flag("checkout");
    this.adapters.flags().save(SHOP, first, Revision.NONE);
    Flag second = Samples.nextRevision(first);
    this.adapters.flags().save(SHOP, second, first.revision());

    assertConflict(
        () -> this.adapters.flags().save(SHOP, Samples.nextRevision(second), first.revision()));
    assertThat(this.adapters.flags().find(SHOP, first.key())).contains(second);
  }

  @Test
  void rejectsCreatingAFlagThatAlreadyExists() {
    Flag flag = Samples.flag("checkout");
    this.adapters.flags().save(SHOP, flag, Revision.NONE);

    assertConflict(() -> this.adapters.flags().save(SHOP, flag, Revision.NONE));
  }

  @Test
  void rejectsUpdatingAFlagThatDoesNotExist() {
    Flag flag = Samples.nextRevision(Samples.flag("checkout"));

    assertConflict(() -> this.adapters.flags().save(SHOP, flag, Revision.FIRST));
  }

  @Test
  void letsOnlyOneOfTwoConcurrentWritersWin() {
    Flag first = Samples.flag("checkout");
    this.adapters.flags().save(SHOP, first, Revision.NONE);
    Flag second = Samples.nextRevision(first);

    List<Concurrently.Outcome<Boolean>> outcomes =
        Concurrently.run(
            8,
            () -> {
              this.adapters.flags().save(SHOP, second, first.revision());
              return true;
            });

    assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
    assertThat(outcomes)
        .filteredOn(outcome -> !outcome.succeeded())
        .hasSize(7)
        .allSatisfy(outcome -> assertThat(outcome.failure()).isInstanceOf(FlagtideException.class));
  }

  @Test
  void aRolledBackTransactionLeavesNoTrace() {
    Flag created = Samples.flag("checkout");
    Flag existing = Samples.flag("existing");
    this.adapters.flags().save(SHOP, existing, Revision.NONE);

    assertThatThrownBy(
            () ->
                this.adapters
                    .transactions()
                    .inTransaction(
                        () -> {
                          this.adapters.flags().save(SHOP, created, Revision.NONE);
                          this.adapters
                              .flags()
                              .save(SHOP, Samples.nextRevision(existing), existing.revision());
                          throw new IllegalStateException("boom");
                        }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(this.adapters.flags().find(SHOP, created.key())).isEmpty();
    assertThat(this.adapters.flags().find(SHOP, existing.key())).contains(existing);
  }

  @Test
  void aCommittedTransactionIsVisibleAfterwards() {
    Flag flag = Samples.flag("checkout");

    this.adapters
        .transactions()
        .inTransaction(
            () -> {
              this.adapters.flags().save(SHOP, flag, Revision.NONE);
              assertThat(this.adapters.flags().find(SHOP, flag.key())).contains(flag);
              return null;
            });

    assertThat(this.adapters.flags().find(SHOP, flag.key())).contains(flag);
  }

  @Test
  void aReadOnlyTransactionRejectsWrites() {
    assertThatThrownBy(
            () ->
                this.adapters
                    .transactions()
                    .inReadOnlyTransaction(
                        () -> {
                          this.adapters.flags().save(SHOP, Samples.flag("checkout"), Revision.NONE);
                          return null;
                        }))
        .isInstanceOf(RuntimeException.class);
    assertThat(this.adapters.flags().findAll(SHOP)).isEmpty();
  }
}
