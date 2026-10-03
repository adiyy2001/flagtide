package dev.flagwire.adapter.out.memory;

import dev.flagwire.application.port.out.ChangeFeed;
import dev.flagwire.application.port.out.ChangeNotification;
import dev.flagwire.application.port.out.Subscription;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class MemoryChangeFeed implements ChangeFeed {

  private final List<Consumer<ChangeNotification>> listeners = new CopyOnWriteArrayList<>();

  @Override
  public Subscription subscribe(Consumer<ChangeNotification> listener, Runnable onResync) {
    this.listeners.add(listener);
    return () -> this.listeners.remove(listener);
  }

  void publish(ChangeNotification notification) {
    this.listeners.forEach(listener -> listener.accept(notification));
  }
}
