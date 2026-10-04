package dev.flagtide.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.WebSocketClientConnection;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

final class StreamProbe implements AutoCloseable {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Duration PATIENCE = Duration.ofSeconds(10);

  private final LinkedBlockingQueue<JsonNode> frames;
  private final CompletableFuture<CloseReason> closed;
  private final WebSocketClientConnection connection;

  private StreamProbe(
      LinkedBlockingQueue<JsonNode> frames,
      CompletableFuture<CloseReason> closed,
      WebSocketClientConnection connection) {
    this.frames = frames;
    this.closed = closed;
    this.connection = connection;
  }

  static StreamProbe connect(BasicWebSocketConnector connector, URI base) {
    LinkedBlockingQueue<JsonNode> frames = new LinkedBlockingQueue<>();
    CompletableFuture<CloseReason> closed = new CompletableFuture<>();
    BasicWebSocketConnector prepared =
        connector
            .baseUri(base)
            .path("/sdk/v1/stream")
            .onTextMessage((connection, text) -> frames.add(parse(text)))
            .onClose((connection, reason) -> closed.complete(reason));
    return new StreamProbe(frames, closed, prepared.connectAndAwait());
  }

  private static JsonNode parse(String text) {
    try {
      return MAPPER.readTree(text);
    } catch (Exception malformed) {
      throw new IllegalStateException(malformed);
    }
  }

  void hello(String sdkKey) {
    this.send("{\"t\":\"hello\",\"sdkKey\":\"%s\"}".formatted(sdkKey));
  }

  void hello(String sdkKey, long version) {
    this.send("{\"t\":\"hello\",\"sdkKey\":\"%s\",\"version\":%d}".formatted(sdkKey, version));
  }

  void ack(long version) {
    this.send("{\"t\":\"ack\",\"v\":%d}".formatted(version));
  }

  void send(String text) {
    this.connection.sendTextAndAwait(text);
  }

  JsonNode next(String type) throws InterruptedException {
    long deadline = System.nanoTime() + PATIENCE.toNanos();
    while (System.nanoTime() < deadline) {
      JsonNode frame = this.frames.poll(100, TimeUnit.MILLISECONDS);
      if (frame == null) {
        continue;
      }
      if (frame.get("t").asText().equals(type)) {
        return frame;
      }
      if (!frame.get("t").asText().equals("hb")) {
        throw new AssertionError("expected " + type + " but got " + frame);
      }
    }
    throw new AssertionError("no " + type + " frame in time");
  }

  CloseReason awaitClose() throws Exception {
    return this.closed.get(PATIENCE.toSeconds(), TimeUnit.SECONDS);
  }

  void assertNoFrame(Duration quiet) throws InterruptedException {
    JsonNode frame = this.frames.poll(quiet.toMillis(), TimeUnit.MILLISECONDS);
    assertThat(frame).isNull();
  }

  @Override
  public void close() {
    if (!this.connection.isClosed()) {
      this.connection.closeAndAwait();
    }
  }
}
