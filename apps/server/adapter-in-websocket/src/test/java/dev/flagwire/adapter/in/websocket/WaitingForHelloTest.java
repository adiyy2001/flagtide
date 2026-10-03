package dev.flagwire.adapter.in.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WaitingForHelloTest {

  @Test
  void admitsUpToTheLimitThenRefuses() {
    WaitingForHello waiting = new WaitingForHello(2);

    assertThat(waiting.tryEnter()).isTrue();
    assertThat(waiting.tryEnter()).isTrue();
    assertThat(waiting.tryEnter()).isFalse();
    assertThat(waiting.count()).isEqualTo(2);
  }

  @Test
  void leavingMakesRoom() {
    WaitingForHello waiting = new WaitingForHello(1);
    waiting.tryEnter();

    waiting.leave();

    assertThat(waiting.tryEnter()).isTrue();
  }
}
