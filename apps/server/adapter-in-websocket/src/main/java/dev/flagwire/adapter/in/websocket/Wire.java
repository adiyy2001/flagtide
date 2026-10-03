package dev.flagwire.adapter.in.websocket;

import io.smallrye.mutiny.Uni;

interface Wire {

  Uni<Void> sendText(String frame);

  Uni<Void> close(int code, String reason);

  boolean isClosed();
}
