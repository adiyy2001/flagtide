package dev.flagwire.adapter.out.postgres;

import static dev.flagwire.application.testing.Samples.NOW;
import static dev.flagwire.application.testing.Samples.SHOP_DEV;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagwire.application.change.Change;
import dev.flagwire.domain.value.EnvironmentVersion;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ReadOnlySnapshotTest {

  private static final List<Change> ONE_CHANGE = List.of(new Change.FlagRemoved("a"));

  @Inject DataSource dataSource;

  @Test
  void aReadOnlyTransactionSeesOneConsistentSnapshot() throws InterruptedException {
    PostgresAdapters adapters =
        PostgresTestAdapters.onEmptyDatabase(this.dataSource, 100).adapters();
    adapters.changeLog().append(SHOP_DEV, NOW, ONE_CHANGE);

    List<EnvironmentVersion> seen =
        adapters
            .transactions()
            .inReadOnlyTransaction(
                () -> {
                  EnvironmentVersion before = adapters.changeLog().currentVersion(SHOP_DEV);
                  this.appendFromAnotherThread(adapters);
                  EnvironmentVersion after = adapters.changeLog().currentVersion(SHOP_DEV);
                  return List.of(before, after);
                });

    assertThat(seen).containsExactly(EnvironmentVersion.of(1), EnvironmentVersion.of(1));
    assertThat(adapters.changeLog().currentVersion(SHOP_DEV)).isEqualTo(EnvironmentVersion.of(2));
  }

  @Test
  void aReadOnlyTransactionCannotWrite() {
    PostgresAdapters adapters =
        PostgresTestAdapters.onEmptyDatabase(this.dataSource, 100).adapters();

    assertThatThrownBy(
            () ->
                adapters
                    .transactions()
                    .inReadOnlyTransaction(
                        () -> adapters.changeLog().append(SHOP_DEV, NOW, ONE_CHANGE)))
        .isInstanceOf(RuntimeException.class);
  }

  private void appendFromAnotherThread(PostgresAdapters adapters) {
    Thread writer = new Thread(() -> adapters.changeLog().append(SHOP_DEV, NOW, ONE_CHANGE));
    writer.start();
    try {
      writer.join();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
