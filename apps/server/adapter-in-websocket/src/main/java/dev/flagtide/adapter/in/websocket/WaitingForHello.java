package dev.flagtide.adapter.in.websocket;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class WaitingForHello {

  private final int limit;
  private final AtomicInteger waiting = new AtomicInteger();

  @Inject
  public WaitingForHello(StreamSettings settings) {
    this(settings.maxWaitingForHello());
  }

  WaitingForHello(int limit) {
    this.limit = limit;
  }

  boolean tryEnter() {
    if (this.waiting.incrementAndGet() > this.limit) {
      this.waiting.decrementAndGet();
      return false;
    }
    return true;
  }

  void leave() {
    this.waiting.decrementAndGet();
  }

  int count() {
    return this.waiting.get();
  }
}
