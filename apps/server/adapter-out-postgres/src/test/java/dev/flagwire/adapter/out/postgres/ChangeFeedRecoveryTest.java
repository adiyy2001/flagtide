package dev.flagwire.adapter.out.postgres;

import static dev.flagwire.application.testing.Samples.NOW;
import static dev.flagwire.application.testing.Samples.SHOP_DEV;
import static org.assertj.core.api.Assertions.assertThat;

import dev.flagwire.application.change.Change;
import dev.flagwire.application.port.out.ChangeNotification;
import dev.flagwire.application.port.out.Subscription;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ChangeFeedRecoveryTest {

  private static final String LISTENER_NAME = "flagwire-recovery-listener";
  private static final List<Change> ONE_CHANGE = List.of(new Change.FlagRemoved("a"));

  @Inject DataSource dataSource;

  private Vertx vertx;
  private PostgresAdapters adapters;
  private PostgresChangeFeed feed;
  private Subscription subscription;
  private final List<ChangeNotification> received = new CopyOnWriteArrayList<>();
  private final AtomicInteger resyncs = new AtomicInteger();

  @BeforeEach
  void listen() {
    this.adapters = PostgresTestAdapters.onEmptyDatabase(this.dataSource, 100).adapters();
    this.vertx = Vertx.vertx();
    this.feed =
        new PostgresChangeFeed(this.vertx, PostgresTestAdapters.listenerOptions(LISTENER_NAME));
    this.subscription = this.feed.subscribe(this.received::add, this.resyncs::incrementAndGet);
  }

  @AfterEach
  void stop() {
    this.subscription.close();
    this.feed.close();
    this.vertx.close();
  }

  private long listenerBackends() {
    return Jdbi.create(this.dataSource)
        .withHandle(
            handle ->
                handle
                    .createQuery(
                        "SELECT count(*) FROM pg_stat_activity WHERE application_name = :name")
                    .bind("name", LISTENER_NAME)
                    .mapTo(Long.class)
                    .one());
  }

  private void terminateListener() {
    Jdbi.create(this.dataSource)
        .useHandle(
            handle ->
                handle
                    .createQuery(
                        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE application_name = :name")
                    .bind("name", LISTENER_NAME)
                    .mapTo(Boolean.class)
                    .list());
  }

  @Test
  void asksForAResyncWhenItStartsListening() {
    assertThat(this.resyncs.get()).isEqualTo(1);
  }

  @Test
  void listensAgainAndAsksForAResyncAfterItsConnectionWasKilled() {
    this.adapters.changeLog().append(SHOP_DEV, NOW, ONE_CHANGE);
    Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> this.received.size() == 1);

    this.terminateListener();
    Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> this.resyncs.get() == 2);
    this.adapters.changeLog().append(SHOP_DEV, NOW, ONE_CHANGE);

    Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> this.received.size() == 2);
    assertThat(this.received)
        .extracting(notification -> notification.version().value())
        .containsExactly(1L, 2L);
    assertThat(this.listenerBackends()).isEqualTo(1);
  }

  @Test
  void ignoresAMalformedPayloadAndKeepsDelivering() {
    Jdbi.create(this.dataSource)
        .useHandle(
            handle ->
                handle.execute(
                    "SELECT pg_notify('" + ChangeNotifications.CHANNEL + "', 'garbage')"));
    this.adapters.changeLog().append(SHOP_DEV, NOW, ONE_CHANGE);

    Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> this.received.size() == 1);
  }

  @Test
  void aFailingListenerDoesNotStopTheOthers() {
    List<ChangeNotification> healthy = new CopyOnWriteArrayList<>();
    Subscription failing =
        this.feed.subscribe(
            notification -> {
              throw new IllegalStateException("boom");
            });
    Subscription working = this.feed.subscribe(healthy::add);

    this.adapters.changeLog().append(SHOP_DEV, NOW, ONE_CHANGE);

    Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> healthy.size() == 1);
    failing.close();
    working.close();
  }
}
