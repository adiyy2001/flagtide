package dev.flagtide.bootstrap;

import dev.flagtide.application.port.out.ChangeFeed;
import dev.flagtide.application.port.out.PropagationStats;
import dev.flagtide.application.port.out.Subscription;
import dev.flagtide.application.usecase.PropagateChange;
import dev.flagtide.application.usecase.SendHeartbeats;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Singleton
public class Propagation {

  private static final Logger LOG = Logger.getLogger(Propagation.class);
  private static final long PUBLISH_PERIOD_MILLIS = 1000;

  private final ChangeFeed changeFeed;
  private final PropagateChange propagateChange;
  private final SendHeartbeats sendHeartbeats;
  private final PropagationStats stats;
  private final Duration heartbeatInterval;
  private final ExecutorService propagation =
      Executors.newSingleThreadExecutor(runnable -> daemon(runnable, "flagtide-propagation"));
  private final ScheduledExecutorService scheduler =
      Executors.newScheduledThreadPool(2, runnable -> daemon(runnable, "flagtide-scheduler"));
  private Subscription subscription;

  @Inject
  public Propagation(
      ChangeFeed changeFeed,
      PropagateChange propagateChange,
      SendHeartbeats sendHeartbeats,
      PropagationStats stats,
      @ConfigProperty(name = "flagtide.propagation.heartbeat-interval")
          Duration heartbeatInterval) {
    this.changeFeed = changeFeed;
    this.propagateChange = propagateChange;
    this.sendHeartbeats = sendHeartbeats;
    this.stats = stats;
    this.heartbeatInterval = heartbeatInterval;
  }

  void onStart(@Observes StartupEvent startup) {
    this.subscription =
        this.changeFeed.subscribe(
            notification -> this.submit(() -> this.propagateChange.execute(notification)),
            () -> this.submit(this.propagateChange::resync));
    this.scheduler.scheduleWithFixedDelay(
        () -> this.guarded(this.sendHeartbeats::execute),
        this.heartbeatInterval.toMillis(),
        this.heartbeatInterval.toMillis(),
        TimeUnit.MILLISECONDS);
    this.scheduler.scheduleWithFixedDelay(
        () -> this.guarded(this.stats::publish),
        PUBLISH_PERIOD_MILLIS,
        PUBLISH_PERIOD_MILLIS,
        TimeUnit.MILLISECONDS);
  }

  void onStop(@Observes ShutdownEvent shutdown) {
    if (this.subscription != null) {
      this.subscription.close();
    }
    this.scheduler.shutdownNow();
    this.propagation.shutdownNow();
    this.guarded(this.stats::withdraw);
  }

  private void submit(Runnable work) {
    if (!this.propagation.isShutdown()) {
      this.propagation.execute(() -> this.guarded(work));
    }
  }

  private void guarded(Runnable work) {
    try {
      work.run();
    } catch (RuntimeException failure) {
      LOG.warn("propagation step failed", failure);
    }
  }

  private static Thread daemon(Runnable runnable, String name) {
    Thread thread = new Thread(runnable, name);
    thread.setDaemon(true);
    return thread;
  }
}
