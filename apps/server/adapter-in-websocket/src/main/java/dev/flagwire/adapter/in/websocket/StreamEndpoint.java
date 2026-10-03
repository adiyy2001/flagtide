package dev.flagwire.adapter.in.websocket;

import dev.flagwire.application.stream.ClientFrame;
import dev.flagwire.application.usecase.ConnectClient;
import dev.flagwire.domain.error.FlagwireError;
import dev.flagwire.domain.error.FlagwireException;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.UserData.TypedKey;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@WebSocket(path = "/sdk/v1/stream")
public class StreamEndpoint {

  private static final Logger LOG = Logger.getLogger(StreamEndpoint.class);
  private static final TypedKey<StreamConnection> STATE = new TypedKey<>("flagwire.stream");

  private final ConnectClient connectClient;
  private final StreamSettings settings;
  private final WaitingForHello waiting;
  private final Vertx vertx;

  @Inject
  public StreamEndpoint(
      ConnectClient connectClient, StreamSettings settings, WaitingForHello waiting, Vertx vertx) {
    this.connectClient = connectClient;
    this.settings = settings;
    this.waiting = waiting;
    this.vertx = vertx;
  }

  @OnOpen
  @NonBlocking
  public Uni<Void> opened(WebSocketConnection connection) {
    StreamConnection state =
        new StreamConnection(
            new ConnectionWire(connection), this.waiting, this.settings.maxPendingFrames());
    connection.userData().put(STATE, state);
    if (!state.admit()) {
      return state.reject(CloseCodes.SLOW_CONSUMER, "too many connections are waiting for hello");
    }
    this.vertx.setTimer(
        this.settings.helloTimeout().toMillis(),
        timer -> {
          if (!state.hasHello()) {
            state
                .reject(CloseCodes.NO_HELLO_IN_TIME, "send hello within the deadline")
                .subscribe()
                .with(done -> {}, failure -> {});
          }
        });
    return Uni.createFrom().voidItem();
  }

  @OnTextMessage
  @NonBlocking
  public Uni<Void> message(WebSocketConnection connection, String text) {
    StreamConnection state = connection.userData().get(STATE);
    ClientFrame frame;
    try {
      frame = ClientFrame.parse(text);
    } catch (FlagwireException malformed) {
      return state.reject(CloseCodes.BAD_FRAME, malformed.getMessage());
    }
    return switch (frame) {
      case ClientFrame.Hello hello -> this.hello(state, hello);
      case ClientFrame.Ack ack -> this.ack(state, ack);
    };
  }

  @OnClose
  @NonBlocking
  public void closed(WebSocketConnection connection) {
    StreamConnection state = connection.userData().get(STATE);
    if (state != null) {
      state.closed();
    }
  }

  @OnError
  @NonBlocking
  public Uni<Void> failed(WebSocketConnection connection, Throwable failure) {
    LOG.warnf("stream connection %s failed: %s", connection.id(), failure.toString());
    StreamConnection state = connection.userData().get(STATE);
    return state == null
        ? connection.close()
        : state.reject(CloseCodes.UNAVAILABLE, "the stream failed, reconnect");
  }

  private Uni<Void> hello(StreamConnection state, ClientFrame.Hello hello) {
    if (!state.startHello()) {
      return state.reject(CloseCodes.BAD_FRAME, "hello was already sent");
    }
    ConnectClient.Command command =
        new ConnectClient.Command(hello.sdkKey(), hello.version(), state);
    return Uni.createFrom()
        .item(() -> this.connectClient.execute(command))
        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
        .onItem()
        .invoke(state::attach)
        .onItem()
        .<Void>transform(session -> null)
        .onFailure(FlagwireException.class)
        .recoverWithUni(failure -> state.reject(closeCodeOf(failure), failure.getMessage()))
        .onFailure()
        .recoverWithUni(
            failure -> {
              LOG.error("cannot start a stream", failure);
              return state.reject(CloseCodes.UNAVAILABLE, "try again later");
            });
  }

  private Uni<Void> ack(StreamConnection state, ClientFrame.Ack ack) {
    if (!state.hasSession()) {
      return state.reject(CloseCodes.BAD_FRAME, "send hello before ack");
    }
    state.acknowledge(ack);
    return Uni.createFrom().voidItem();
  }

  private static int closeCodeOf(FlagwireException failure) {
    return failure.error() instanceof FlagwireError.Unauthorized
        ? CloseCodes.UNAUTHORIZED
        : CloseCodes.BAD_FRAME;
  }
}
