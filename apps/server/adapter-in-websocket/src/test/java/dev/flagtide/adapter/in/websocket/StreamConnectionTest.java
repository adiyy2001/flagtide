package dev.flagtide.adapter.in.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StreamConnectionTest {

  private static final int LIMIT = 3;

  private final WaitingForHello waiting = new WaitingForHello(10);

  private StreamConnection over(FakeWire wire) {
    StreamConnection connection = new StreamConnection(wire, this.waiting, LIMIT);
    connection.admit();
    return connection;
  }

  @Test
  void framesGoOutInOrderWhileTheClientKeepsUp() {
    FakeWire wire = new FakeWire(false);
    StreamConnection connection = this.over(wire);

    connection.send("a");
    connection.send("b");

    assertThat(wire.sent()).containsExactly("a", "b");
    assertThat(connection.pendingFrames()).isZero();
  }

  @Test
  void aClientThatFallsBehindTheLimitIsClosedWithSlowConsumer() {
    FakeWire wire = new FakeWire(true);
    StreamConnection connection = this.over(wire);

    for (int index = 0; index <= LIMIT; index++) {
      connection.send("frame-" + index);
    }

    assertThat(wire.closeCodes()).containsExactly(CloseCodes.SLOW_CONSUMER);
    assertThat(wire.sent()).hasSize(LIMIT);
  }

  @Test
  void flushedFramesFreeRoomAgain() {
    FakeWire wire = new FakeWire(true);
    StreamConnection connection = this.over(wire);
    connection.send("a");
    connection.send("b");
    connection.send("c");

    wire.flushOne();
    connection.send("d");

    assertThat(wire.closeCodes()).isEmpty();
    assertThat(connection.pendingFrames()).isEqualTo(LIMIT);
  }

  @Test
  void nothingIsSentAfterTheSlowConsumerClose() {
    FakeWire wire = new FakeWire(true);
    StreamConnection connection = this.over(wire);
    for (int index = 0; index < LIMIT + 3; index++) {
      connection.send("frame-" + index);
    }

    assertThat(wire.closeCodes()).hasSize(1);
    assertThat(wire.sent()).hasSize(LIMIT);
  }

  @Test
  void aRejectionSendsTheErrorFrameThenClosesWithTheCode() {
    FakeWire wire = new FakeWire(false);
    StreamConnection connection = this.over(wire);

    connection.reject(CloseCodes.UNAUTHORIZED, "no").await().indefinitely();

    assertThat(wire.sent()).singleElement().asString().contains("\"code\":4401");
    assertThat(wire.closeCodes()).containsExactly(CloseCodes.UNAUTHORIZED);
  }

  @Test
  void helloIsAcceptedOnlyOnce() {
    StreamConnection connection = this.over(new FakeWire(false));

    assertThat(connection.startHello()).isTrue();
    assertThat(connection.startHello()).isFalse();
  }

  @Test
  void aConnectionStopsCountingAsWaitingAfterHelloOrClose() {
    StreamConnection greeted = this.over(new FakeWire(false));
    StreamConnection abandoned = this.over(new FakeWire(false));
    assertThat(this.waiting.count()).isEqualTo(2);

    greeted.startHello();
    abandoned.closed();
    abandoned.closed();

    assertThat(this.waiting.count()).isZero();
  }
}
