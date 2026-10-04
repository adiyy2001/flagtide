package dev.flagtide.application.contract;

import static dev.flagtide.application.testing.Samples.SHOP_DEV;
import static dev.flagtide.application.testing.Samples.SHOP_PROD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagtide.application.port.out.ChangeNotification;
import dev.flagtide.application.port.out.Subscription;
import dev.flagtide.application.testing.Samples;
import dev.flagtide.domain.value.EnvironmentVersion;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public abstract class ChangeFeedContract extends AdapterContract {

  private static final long WAIT_MILLIS = 5000;

  private void waitUntilReceived(List<ChangeNotification> received, int count)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
    while (received.size() < count && System.nanoTime() < deadline) {
      Thread.sleep(5);
    }
  }

  @Test
  void deliversAVersionPointerAfterAnAppend() throws InterruptedException {
    List<ChangeNotification> received = new CopyOnWriteArrayList<>();
    Subscription subscription = this.adapters.changeFeed().subscribe(received::add);
    try {
      this.adapters.changeLog().append(SHOP_DEV, this.time.now(), Samples.upsert("a"));
      this.adapters.changeLog().append(SHOP_PROD, this.time.now(), Samples.upsert("b"));
      this.waitUntilReceived(received, 2);
    } finally {
      subscription.close();
    }

    assertThat(received)
        .containsExactly(
            new ChangeNotification(SHOP_DEV, EnvironmentVersion.of(1)),
            new ChangeNotification(SHOP_PROD, EnvironmentVersion.of(1)));
  }

  @Test
  void deliversNothingBeforeTheTransactionCommits() throws InterruptedException {
    List<ChangeNotification> received = new CopyOnWriteArrayList<>();
    Subscription subscription = this.adapters.changeFeed().subscribe(received::add);
    try {
      this.adapters
          .transactions()
          .inTransaction(
              () -> {
                this.adapters.changeLog().append(SHOP_DEV, this.time.now(), Samples.upsert("a"));
                assertThat(received).isEmpty();
                return null;
              });
      this.waitUntilReceived(received, 1);
    } finally {
      subscription.close();
    }

    assertThat(received).hasSize(1);
  }

  @Test
  void deliversNothingForARolledBackTransaction() throws InterruptedException {
    List<ChangeNotification> received = new CopyOnWriteArrayList<>();
    Subscription subscription = this.adapters.changeFeed().subscribe(received::add);
    try {
      assertThatThrownBy(
              () ->
                  this.adapters
                      .transactions()
                      .inTransaction(
                          () -> {
                            this.adapters
                                .changeLog()
                                .append(SHOP_DEV, this.time.now(), Samples.upsert("a"));
                            throw new IllegalStateException("boom");
                          }))
          .isInstanceOf(IllegalStateException.class);
      this.adapters.changeLog().append(SHOP_DEV, this.time.now(), Samples.upsert("b"));
      this.waitUntilReceived(received, 1);
    } finally {
      subscription.close();
    }

    assertThat(received)
        .containsExactly(new ChangeNotification(SHOP_DEV, EnvironmentVersion.of(1)));
  }

  @Test
  void stopsDeliveringAfterTheSubscriptionIsClosed() throws InterruptedException {
    List<ChangeNotification> received = new CopyOnWriteArrayList<>();
    Subscription subscription = this.adapters.changeFeed().subscribe(received::add);
    this.adapters.changeLog().append(SHOP_DEV, this.time.now(), Samples.upsert("a"));
    this.waitUntilReceived(received, 1);

    subscription.close();
    this.adapters.changeLog().append(SHOP_DEV, this.time.now(), Samples.upsert("b"));
    Thread.sleep(50);

    assertThat(received).hasSize(1);
  }

  @Test
  void deliversToEverySubscriber() throws InterruptedException {
    List<ChangeNotification> first = new CopyOnWriteArrayList<>();
    List<ChangeNotification> second = new CopyOnWriteArrayList<>();
    Subscription one = this.adapters.changeFeed().subscribe(first::add);
    Subscription two = this.adapters.changeFeed().subscribe(second::add);
    this.adapters.changeLog().append(SHOP_DEV, this.time.now(), Samples.upsert("a"));
    this.waitUntilReceived(first, 1);
    this.waitUntilReceived(second, 1);
    one.close();
    two.close();

    assertThat(first).hasSize(1);
    assertThat(second).hasSize(1);
  }
}
