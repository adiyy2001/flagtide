package dev.flagtide.adapter.in.websocket;

import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;

final class ConnectionWire implements Wire {

  private final WebSocketConnection connection;

  ConnectionWire(WebSocketConnection connection) {
    this.connection = connection;
  }

  @Override
  public Uni<Void> sendText(String frame) {
    return this.connection.sendText(frame);
  }

  @Override
  public Uni<Void> close(int code, String reason) {
    return this.connection.close(new CloseReason(code, reason));
  }

  @Override
  public boolean isClosed() {
    return this.connection.isClosed();
  }
}
