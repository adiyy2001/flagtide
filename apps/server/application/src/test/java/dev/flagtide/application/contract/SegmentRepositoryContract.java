package dev.flagtide.application.contract;

import static dev.flagtide.application.testing.Samples.SHOP_DEV;
import static dev.flagtide.application.testing.Samples.SHOP_PROD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagtide.application.testing.Samples;
import dev.flagtide.domain.error.FlagtideError;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.segment.Segment;
import dev.flagtide.domain.value.Revision;
import dev.flagtide.domain.value.SegmentKey;
import org.junit.jupiter.api.Test;

public abstract class SegmentRepositoryContract extends AdapterContract {

  private static void assertError(Runnable action, Class<? extends FlagtideError> kind) {
    assertThatThrownBy(action::run)
        .isInstanceOf(FlagtideException.class)
        .extracting(failure -> ((FlagtideException) failure).error())
        .isInstanceOf(kind);
  }

  @Test
  void savesFindsAndListsSegmentsInKeyOrder() {
    Segment beta = Samples.segment("beta");
    Segment alpha = Samples.segment("alpha");

    this.adapters.segments().save(SHOP_DEV, beta, Revision.NONE);
    this.adapters.segments().save(SHOP_DEV, alpha, Revision.NONE);

    assertThat(this.adapters.segments().find(SHOP_DEV, beta.key())).contains(beta);
    assertThat(this.adapters.segments().findAll(SHOP_DEV)).containsExactly(alpha, beta);
  }

  @Test
  void keepsEnvironmentsApart() {
    Segment beta = Samples.segment("beta");
    this.adapters.segments().save(SHOP_DEV, beta, Revision.NONE);

    assertThat(this.adapters.segments().find(SHOP_PROD, beta.key())).isEmpty();
    assertThat(this.adapters.segments().findAll(SHOP_PROD)).isEmpty();
  }

  @Test
  void checksTheExpectedRevisionOnSave() {
    Segment first = Samples.segment("beta");
    this.adapters.segments().save(SHOP_DEV, first, Revision.NONE);
    Segment second =
        first
            .update(
                "Renamed", first.included(), first.excluded(), first.rules(), Samples.stamp("a"))
            .next();

    assertError(
        () -> this.adapters.segments().save(SHOP_DEV, first, Revision.NONE),
        FlagtideError.Conflict.class);
    assertError(
        () -> this.adapters.segments().save(SHOP_DEV, second, Revision.of(7)),
        FlagtideError.Conflict.class);
    this.adapters.segments().save(SHOP_DEV, second, first.revision());
    assertThat(this.adapters.segments().find(SHOP_DEV, first.key())).contains(second);
  }

  @Test
  void deletesWithTheExpectedRevision() {
    Segment beta = Samples.segment("beta");
    this.adapters.segments().save(SHOP_DEV, beta, Revision.NONE);

    assertError(
        () -> this.adapters.segments().delete(SHOP_DEV, beta.key(), Revision.of(9)),
        FlagtideError.Conflict.class);
    this.adapters.segments().delete(SHOP_DEV, beta.key(), beta.revision());

    assertThat(this.adapters.segments().find(SHOP_DEV, beta.key())).isEmpty();
  }

  @Test
  void rejectsDeletingASegmentThatDoesNotExist() {
    assertError(
        () -> this.adapters.segments().delete(SHOP_DEV, new SegmentKey("ghost"), Revision.FIRST),
        FlagtideError.NotFound.class);
  }

  @Test
  void rollsBackSavesAndDeletes() {
    Segment kept = Samples.segment("kept");
    Segment added = Samples.segment("added");
    this.adapters.segments().save(SHOP_DEV, kept, Revision.NONE);

    assertThatThrownBy(
            () ->
                this.adapters
                    .transactions()
                    .inTransaction(
                        () -> {
                          this.adapters.segments().save(SHOP_DEV, added, Revision.NONE);
                          this.adapters.segments().delete(SHOP_DEV, kept.key(), kept.revision());
                          throw new IllegalStateException("boom");
                        }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(this.adapters.segments().findAll(SHOP_DEV)).containsExactly(kept);
  }
}
