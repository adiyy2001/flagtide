package dev.flagwire.application.contract;

import static dev.flagwire.application.testing.Samples.SHOP_DEV;
import static dev.flagwire.application.testing.Samples.SHOP_PROD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagwire.application.change.Change;
import dev.flagwire.application.change.ChangeLogEntry;
import dev.flagwire.application.port.out.ChangeLog;
import dev.flagwire.application.testing.Samples;
import dev.flagwire.application.testing.TestAdapters;
import dev.flagwire.domain.value.EnvironmentVersion;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

public abstract class ChangeLogContract extends AdapterContract {

  private EnvironmentVersion appendOne(ChangeLog log, String key) {
    return log.append(SHOP_DEV, this.time.now(), Samples.upsert(key));
  }

  @Test
  void startsAtVersionZeroWithoutEntries() {
    ChangeLog log = this.adapters.changeLog();

    assertThat(log.currentVersion(SHOP_DEV)).isEqualTo(EnvironmentVersion.ZERO);
    assertThat(log.entriesAfter(SHOP_DEV, EnvironmentVersion.ZERO)).isEmpty();
    assertThat(log.oldestRetained(SHOP_DEV)).isEmpty();
  }

  @Test
  void assignsVersionsOneTwoThreeInOrder() {
    ChangeLog log = this.adapters.changeLog();

    assertThat(this.appendOne(log, "a")).isEqualTo(EnvironmentVersion.of(1));
    assertThat(this.appendOne(log, "b")).isEqualTo(EnvironmentVersion.of(2));
    assertThat(this.appendOne(log, "c")).isEqualTo(EnvironmentVersion.of(3));
    assertThat(log.currentVersion(SHOP_DEV)).isEqualTo(EnvironmentVersion.of(3));
  }

  @Test
  void keepsEnvironmentVersionsIndependent() {
    ChangeLog log = this.adapters.changeLog();
    this.appendOne(log, "a");
    this.appendOne(log, "b");

    assertThat(log.append(SHOP_PROD, this.time.now(), Samples.upsert("c")))
        .isEqualTo(EnvironmentVersion.of(1));
    assertThat(log.currentVersion(SHOP_PROD)).isEqualTo(EnvironmentVersion.of(1));
  }

  @Test
  void returnsTheEntriesAfterAVersionInOrderWithTheirContent() {
    ChangeLog log = this.adapters.changeLog();
    this.appendOne(log, "a");
    this.time.advance(Duration.ofSeconds(1));
    this.appendOne(log, "b");
    this.time.advance(Duration.ofSeconds(1));
    this.appendOne(log, "c");

    List<ChangeLogEntry> entries = log.entriesAfter(SHOP_DEV, EnvironmentVersion.of(1));

    assertThat(entries).extracting(entry -> entry.version().value()).containsExactly(2L, 3L);
    assertThat(entries.get(0).changes()).containsExactly(new Change.FlagRemoved("b"));
    assertThat(entries.get(0).committedAt()).isEqualTo(Samples.NOW.plusSeconds(1));
    assertThat(log.entriesAfter(SHOP_DEV, EnvironmentVersion.of(3))).isEmpty();
  }

  @Test
  void keepsOnlyTheConfiguredNumberOfEntries() {
    ChangeLog log = this.createAdapters(this.time, 5).changeLog();

    IntStream.range(0, 12).forEach(index -> this.appendOne(log, "k" + index));

    assertThat(log.currentVersion(SHOP_DEV)).isEqualTo(EnvironmentVersion.of(12));
    assertThat(log.oldestRetained(SHOP_DEV)).contains(EnvironmentVersion.of(8));
    assertThat(log.entriesAfter(SHOP_DEV, EnvironmentVersion.ZERO))
        .extracting(entry -> entry.version().value())
        .containsExactly(8L, 9L, 10L, 11L, 12L);
  }

  @Test
  void hasNoGapsAndNoDuplicatesUnderConcurrentAppends() {
    ChangeLog log = this.adapters.changeLog();
    int threads = 8;
    int appendsPerThread = 25;

    List<Concurrently.Outcome<Long>> outcomes =
        Concurrently.run(
            threads,
            () ->
                IntStream.range(0, appendsPerThread)
                    .mapToLong(
                        index ->
                            this.adapters
                                .transactions()
                                .inTransaction(() -> this.appendOne(log, "k" + index))
                                .value())
                    .max()
                    .orElseThrow());

    assertThat(outcomes).allMatch(Concurrently.Outcome::succeeded);
    long total = (long) threads * appendsPerThread;
    assertThat(log.currentVersion(SHOP_DEV)).isEqualTo(EnvironmentVersion.of(total));
    assertThat(log.entriesAfter(SHOP_DEV, EnvironmentVersion.ZERO))
        .extracting(entry -> entry.version().value())
        .containsExactlyElementsOf(LongStream.rangeClosed(1, total).boxed().toList());
  }

  @Test
  void aRolledBackAppendDoesNotConsumeAVersion() {
    ChangeLog log = this.adapters.changeLog();
    this.appendOne(log, "a");

    assertThatThrownBy(
            () ->
                this.adapters
                    .transactions()
                    .inTransaction(
                        () -> {
                          this.appendOne(log, "b");
                          this.appendOne(log, "c");
                          throw new IllegalStateException("boom");
                        }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(log.currentVersion(SHOP_DEV)).isEqualTo(EnvironmentVersion.of(1));
    assertThat(this.appendOne(log, "d")).isEqualTo(EnvironmentVersion.of(2));
    assertThat(log.entriesAfter(SHOP_DEV, EnvironmentVersion.ZERO))
        .extracting(entry -> entry.version().value())
        .containsExactly(1L, 2L);
  }

  @Test
  void aRolledBackAppendRestoresTrimmedEntries() {
    TestAdapters small = this.createAdapters(this.time, 3);
    ChangeLog log = small.changeLog();
    IntStream.range(0, 3).forEach(index -> this.appendOne(log, "k" + index));

    assertThatThrownBy(
            () ->
                small
                    .transactions()
                    .inTransaction(
                        () -> {
                          this.appendOne(log, "late");
                          throw new IllegalStateException("boom");
                        }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(log.oldestRetained(SHOP_DEV)).contains(EnvironmentVersion.of(1));
    assertThat(log.currentVersion(SHOP_DEV)).isEqualTo(EnvironmentVersion.of(3));
  }
}
