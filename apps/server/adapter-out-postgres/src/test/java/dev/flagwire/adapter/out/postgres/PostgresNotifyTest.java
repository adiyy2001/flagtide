package dev.flagwire.adapter.out.postgres;

import static dev.flagwire.application.testing.Samples.NOW;
import static dev.flagwire.application.testing.Samples.SHOP_DEV;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagwire.application.change.Change;
import dev.flagwire.application.port.out.ChangeNotification;
import dev.flagwire.domain.value.EnvironmentVersion;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

@QuarkusTest
class PostgresNotifyTest {

  private static final List<Change> ONE_CHANGE = List.of(new Change.FlagRemoved("a"));

  @Inject DataSource dataSource;

  private PostgresAdapters adapters;
  private Connection listener;

  @BeforeEach
  void listen() throws SQLException {
    this.adapters = PostgresTestAdapters.onEmptyDatabase(this.dataSource, 100).adapters();
    this.listener = this.dataSource.getConnection();
    try (Statement statement = this.listener.createStatement()) {
      statement.execute("LISTEN " + ChangeNotifications.CHANNEL);
    }
  }

  @AfterEach
  void stopListening() throws SQLException {
    this.listener.close();
  }

  private List<ChangeNotification> received() throws SQLException {
    PGNotification[] notifications = this.listener.unwrap(PGConnection.class).getNotifications(500);
    return notifications == null
        ? List.of()
        : Arrays.stream(notifications)
            .map(notification -> ChangeNotifications.parse(notification.getParameter()))
            .toList();
  }

  @Test
  void notifiesWithAPointerWhenTheTransactionCommits() throws SQLException {
    this.adapters.changeLog().append(SHOP_DEV, NOW, ONE_CHANGE);

    assertThat(this.received())
        .containsExactly(new ChangeNotification(SHOP_DEV, EnvironmentVersion.of(1)));
  }

  @Test
  void notifiesNothingBeforeTheCommit() throws SQLException {
    this.adapters
        .transactions()
        .inTransaction(
            () -> {
              this.adapters.changeLog().append(SHOP_DEV, NOW, ONE_CHANGE);
              try {
                assertThat(this.received()).isEmpty();
              } catch (SQLException failure) {
                throw new IllegalStateException(failure);
              }
              return null;
            });

    assertThat(this.received()).hasSize(1);
  }

  @Test
  void notifiesNothingForARolledBackTransaction() throws SQLException {
    assertThatThrownBy(
            () ->
                this.adapters
                    .transactions()
                    .inTransaction(
                        () -> {
                          this.adapters.changeLog().append(SHOP_DEV, NOW, ONE_CHANGE);
                          throw new IllegalStateException("boom");
                        }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(this.received()).isEmpty();
  }

  @Test
  void deliversTwoWritesInVersionOrder() throws SQLException {
    this.adapters.changeLog().append(SHOP_DEV, NOW, ONE_CHANGE);
    this.adapters.changeLog().append(SHOP_DEV, NOW, ONE_CHANGE);

    assertThat(this.received())
        .extracting(notification -> notification.version().value())
        .containsExactly(1L, 2L);
  }
}
