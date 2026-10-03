package dev.flagwire.adapter.in.websocket;

import dev.flagwire.application.stream.ClientFrame;
import dev.flagwire.application.stream.Frames;
import dev.flagwire.application.stream.StreamClient;
import dev.flagwire.application.stream.StreamSession;
import io.smallrye.mutiny.Uni;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class StreamConnection implements StreamClient {

  private static final String SLOW_CONSUMER_MESSAGE = "the client does not keep up";

  private final Wire wire;
  private final WaitingForHello waiting;
  private final int maxPendingFrames;
  private final AtomicInteger pending = new AtomicInteger();
  private final AtomicBoolean slow = new AtomicBoolean();
  private final AtomicBoolean waitingForHello = new AtomicBoolean();
  private final AtomicBoolean helloReceived = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();
  private volatile StreamSession session;

  StreamConnection(Wire wire, WaitingForHello waiting, int maxPendingFrames) {
    this.wire = wire;
    this.waiting = waiting;
    this.maxPendingFrames = maxPendingFrames;
  }

  boolean admit() {
    boolean admitted = this.waiting.tryEnter();
    this.waitingForHello.set(admitted);
    return admitted;
  }

  boolean startHello() {
    boolean first = this.helloReceived.compareAndSet(false, true);
    if (first) {
      this.stopWaiting();
    }
    return first;
  }

  boolean hasHello() {
    return this.helloReceived.get();
  }

  void attach(StreamSession established) {
    this.session = established;
    if (this.closed.get()) {
      established.close();
    }
  }

  void acknowledge(ClientFrame.Ack ack) {
    StreamSession current = this.session;
    if (current != null) {
      current.acknowledge(ack.version());
    }
  }

  boolean hasSession() {
    return this.session != null;
  }

  void closed() {
    this.closed.set(true);
    this.stopWaiting();
    StreamSession current = this.session;
    if (current != null) {
      current.close();
    }
  }

  int pendingFrames() {
    return this.pending.get();
  }

  @Override
  public void send(String frame) {
    if (this.wire.isClosed() || this.slow.get()) {
      return;
    }
    if (this.pending.incrementAndGet() > this.maxPendingFrames) {
      this.pending.decrementAndGet();
      this.dropSlowConsumer();
      return;
    }
    this.wire
        .sendText(frame)
        .subscribe()
        .with(done -> this.pending.decrementAndGet(), failure -> this.pending.decrementAndGet());
  }

  Uni<Void> reject(int code, String message) {
    if (this.wire.isClosed()) {
      return Uni.createFrom().voidItem();
    }
    return this.wire
        .sendText(Frames.error(code, message))
        .onFailure()
        .recoverWithNull()
        .chain(() -> this.wire.close(code, message))
        .onFailure()
        .recoverWithNull();
  }

  private void dropSlowConsumer() {
    if (this.slow.compareAndSet(false, true)) {
      this.wire
          .close(CloseCodes.SLOW_CONSUMER, SLOW_CONSUMER_MESSAGE)
          .subscribe()
          .with(done -> {}, failure -> {});
    }
  }

  private void stopWaiting() {
    if (this.waitingForHello.compareAndSet(true, false)) {
      this.waiting.leave();
    }
  }
}
