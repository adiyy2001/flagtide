package dev.flagwire.application.port.out;

import java.util.function.Consumer;

public interface ChangeFeed {

  Subscription subscribe(Consumer<ChangeNotification> listener, Runnable onResync);

  default Subscription subscribe(Consumer<ChangeNotification> listener) {
    return this.subscribe(listener, () -> {});
  }
}
