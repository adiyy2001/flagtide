package dev.flagwire.adapter.in.websocket;

import io.smallrye.mutiny.Uni;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class FakeWire implements Wire {

  private final List<String> sent = new ArrayList<>();
  private final List<CompletableFuture<Void>> inFlight = new ArrayList<>();
  private final List<Integer> closeCodes = new ArrayList<>();
  private final boolean stalled;
  private boolean closed;

  FakeWire(boolean stalled) {
    this.stalled = stalled;
  }

  @Override
  public Uni<Void> sendText(String frame) {
    this.sent.add(frame);
    if (!this.stalled) {
      return Uni.createFrom().voidItem();
    }
    CompletableFuture<Void> future = new CompletableFuture<>();
    this.inFlight.add(future);
    return Uni.createFrom().completionStage(future);
  }

  @Override
  public Uni<Void> close(int code, String reason) {
    this.closeCodes.add(code);
    this.closed = true;
    return Uni.createFrom().voidItem();
  }

  @Override
  public boolean isClosed() {
    return this.closed;
  }

  void flushOne() {
    this.inFlight.removeFirst().complete(null);
  }

  List<String> sent() {
    return List.copyOf(this.sent);
  }

  List<Integer> closeCodes() {
    return List.copyOf(this.closeCodes);
  }
}
