package dev.flagwire.adapter.in.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OriginCheckTest {

  @Test
  void anOriginWithTheHostOfTheRequestIsSameOrigin() {
    assertThat(OriginCheck.sameOrigin("http://127.0.0.1:8081", "127.0.0.1:8081")).isTrue();
    assertThat(OriginCheck.sameOrigin("https://Flags.Example", "flags.example")).isTrue();
  }

  @Test
  void anotherHostOrPortIsNot() {
    assertThat(OriginCheck.sameOrigin("http://evil.test", "127.0.0.1:8081")).isFalse();
    assertThat(OriginCheck.sameOrigin("http://127.0.0.1:9", "127.0.0.1:8081")).isFalse();
  }

  @Test
  void aMissingHostOrASchemelessOriginIsNot() {
    assertThat(OriginCheck.sameOrigin("http://a.test", null)).isFalse();
    assertThat(OriginCheck.sameOrigin("a.test", "a.test")).isFalse();
  }
}
