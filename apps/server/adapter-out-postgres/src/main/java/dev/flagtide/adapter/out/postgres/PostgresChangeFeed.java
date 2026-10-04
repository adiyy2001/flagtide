package dev.flagtide.adapter.out.postgres;

import dev.flagtide.application.port.out.ChangeFeed;
import dev.flagtide.application.port.out.ChangeNotification;
import dev.flagtide.application.port.out.Subscription;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.pubsub.PgSubscriber;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.jboss.logging.Logger;

public final class PostgresChangeFeed implements ChangeFeed, AutoCloseable {

  private static final Logger LOG = Logger.getLogger(PostgresChangeFeed.class);

  private static final long START_TIMEOUT_SECONDS = 15;
  private static final long MAX_RETRY_DELAY_MILLIS = 5000;
  private static final long FIRST_RETRY_DELAY_MILLIS = 100;

  private record Listener(Consumer<ChangeNotification> onChange, Runnable onResync) {}

  private final Vertx vertx;
  private final PgConnectOptions options;
  private final List<Listener> listeners = new CopyOnWriteArrayList<>();
  private final CompletableFuture<Void> listening = new CompletableFuture<>();
  private PgSubscriber subscriber;
  private boolean started;
  private volatile boolean closed;

  public PostgresChangeFeed(Vertx vertx, PgConnectOptions options) {
    this.vertx = vertx;
    this.options = options;
  }

  @Override
  public Subscription subscribe(Consumer<ChangeNotification> listener, Runnable onResync) {
    Listener entry = new Listener(listener, onResync);
    this.listeners.add(entry);
    this.start();
    this.awaitListening();
    return () -> this.listeners.remove(entry);
  }

  @Override
  public void close() {
    this.closed = true;
    PgSubscriber current;
    synchronized (this) {
      current = this.subscriber;
    }
    if (current != null) {
      current.close();
    }
  }

  private synchronized void start() {
    if (this.started) {
      return;
    }
    this.started = true;
    this.connect(0);
  }

  private void awaitListening() {
    try {
      this.listening.get(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (TimeoutException stillConnecting) {
      LOG.warnf(
          "the change feed is not listening yet, it keeps trying in the background (%s)",
          stillConnecting.getClass().getSimpleName());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException failure) {
      throw new IllegalStateException("the change feed failed to start", failure.getCause());
    }
  }

  private void connect(int attempt) {
    PgSubscriber created =
        PgSubscriber.subscriber(this.vertx, this.options)
            .reconnectPolicy(retries -> retryDelay(retries));
    synchronized (this) {
      this.subscriber = created;
    }
    created
        .channel(ChangeNotifications.CHANNEL)
        .subscribeHandler(ignored -> this.onListening())
        .handler(this::dispatch);
    created
        .connect()
        .onFailure(
            failure -> {
              LOG.warnf("cannot listen for changes, retrying: %s", failure.getMessage());
              if (!this.closed) {
                this.vertx.setTimer(retryDelay(attempt), timer -> this.connect(attempt + 1));
              }
            });
  }

  private void onListening() {
    this.listening.complete(null);
    this.listeners.forEach(listener -> this.guarded(listener.onResync()::run));
  }

  private void dispatch(String payload) {
    ChangeNotification notification;
    try {
      notification = ChangeNotifications.parse(payload);
    } catch (RuntimeException malformed) {
      LOG.warnf("ignoring a malformed change notification: %s", payload);
      return;
    }
    this.listeners.forEach(
        listener -> this.guarded(() -> listener.onChange().accept(notification)));
  }

  private void guarded(Runnable work) {
    try {
      work.run();
    } catch (RuntimeException failure) {
      LOG.error("a change feed listener failed", failure);
    }
  }

  private static long retryDelay(int attempt) {
    return Math.min(MAX_RETRY_DELAY_MILLIS, FIRST_RETRY_DELAY_MILLIS << Math.min(attempt, 6));
  }
}
